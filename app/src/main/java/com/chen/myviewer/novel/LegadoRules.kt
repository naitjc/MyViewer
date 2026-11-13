package com.chen.myviewer.novel

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.mozilla.javascript.*
import java.util.concurrent.TimeUnit

/** Evaluates source rules against HTML, JSON or the result of the preceding rule. */
internal class LegadoRules(private val variables: Map<String, Any> = emptyMap(), private val ajax: (String) -> String) {
    companion object {
        // Android ICU requires escaped closing braces in this expression.
        internal val TEMPLATE_PATTERN = Regex("\\{\\{([\\s\\S]*?)\\}\\}")
        internal fun replaceText(text: String, pattern: String, replacement: String, firstMatch: Boolean = false): String = try {
            val regex = Regex(pattern)
            if (firstMatch) regex.find(text)?.value?.let { regex.replaceFirst(it, replacement) }.orEmpty() else regex.replace(text, replacement)
        }
        catch (error: StackOverflowError) { throw IllegalArgumentException("小说源正则递归过深，请更新书源。", error) }
    }
    private val stored = mutableMapOf<String, String>()
    private var content: Any? = null
    fun setContent(value: Any?) { content = value }
    private var deadline = Long.MAX_VALUE
    private val factory = object : ContextFactory() {
        override fun makeContext(): Context = super.makeContext().apply {
            optimizationLevel = -1
            languageVersion = Context.VERSION_ES6
            instructionObserverThreshold = 10_000
            setClassShutter { false }
        }
        override fun observeInstructionCount(cx: Context, instructionCount: Int) { checkActive() }
    }
    private fun checkActive() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("小说源操作已取消")
        check(System.nanoTime() < deadline) { "小说源脚本执行超时。" }
    }
    fun script(code: String, result: Any? = "", baseUrl: String = ""): Any? {
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        return factory.call(ContextAction { cx ->
            val scope = cx.initSafeStandardObjects()
            val conversion = LegadoScriptValues(cx, scope, ::checkActive)
            fun put(name: String, value: Any?) { ScriptableObject.putProperty(scope, name, conversion.toJs(value)) }
            fun function(action: (Array<out Any>) -> Any?) = conversion.function(action)
            variables.forEach(::put)
            (ScriptableObject.getProperty(scope, "book") as? Scriptable)?.let { book ->
                val data = variables["book"] as? JSONObject
                ScriptableObject.putProperty(book, "getBookUrl", function { data?.optString("bookUrl").orEmpty() })
                ScriptableObject.putProperty(book, "getOrigin", function { data?.optString("origin").orEmpty() })
            }
            put("baseUrl", baseUrl)
            val sourceContent = content ?: result
            put("result", result)
            put("src", when (sourceContent) { is Element -> sourceContent.outerHtml(); else -> sourceContent?.toString().orEmpty() })
            val bridge = cx.newObject(scope)
            ScriptableObject.putProperty(bridge, "ajax", function { ajax(Context.toString(it.firstOrNull() ?: "")) })
            ScriptableObject.putProperty(bridge, "getString", function { text(content ?: result, Context.toString(it.firstOrNull() ?: ""), baseUrl) })
            ScriptableObject.putProperty(bridge, "getStringList", function { conversion.toJs(values(content ?: result, Context.toString(it.firstOrNull() ?: ""), baseUrl).map { value -> if (value is Element) value.text() else value.toString() }) })
            ScriptableObject.putProperty(bridge, "get", function { stored[Context.toString(it.firstOrNull() ?: "")] ?: "" })
            ScriptableObject.putProperty(bridge, "put", function { stored[Context.toString(it[0])] = Context.toString(it[1]); it[1] })
            val getElements = function { conversion.toJs(values(content ?: result, Context.toString(it.firstOrNull() ?: ""), baseUrl)) }
            ScriptableObject.putProperty(bridge, "getElement", getElements)
            ScriptableObject.putProperty(bridge, "getElements", getElements)
            ScriptableObject.putProperty(bridge, "setContent", function { content = conversion.fromJs(it.firstOrNull()); put("src", content); null })
            ScriptableObject.putProperty(bridge, "log", function { null })
            ScriptableObject.putProperty(bridge, "base64Decode", function { android.util.Base64.decode(Context.toString(it.firstOrNull() ?: ""), android.util.Base64.DEFAULT).toString(Charsets.UTF_8) })
            ScriptableObject.putProperty(bridge, "base64Encode", function { android.util.Base64.encodeToString(Context.toString(it.firstOrNull() ?: "").toByteArray(), android.util.Base64.NO_WRAP) })
            ScriptableObject.putProperty(bridge, "md5Encode", function { java.security.MessageDigest.getInstance("MD5").digest(Context.toString(it.firstOrNull() ?: "").toByteArray()).joinToString("") { byte -> "%02x".format(byte) } })
            ScriptableObject.putProperty(scope, "java", bridge)
            conversion.fromJs(cx.evaluateString(scope, code, "novel-source", 1, null))
        })
    }
    fun template(rule: String, data: Any?, baseUrl: String): String = TEMPLATE_PATTERN.replace(rule) { match ->
        val expression = match.groupValues[1].trim()
        if (expression.startsWith('$') || expression.startsWith('@')) text(data, expression.removePrefix("@"), baseUrl)
        else script(expression, data, baseUrl).toString()
    }
    fun text(data: Any?, rule: String, baseUrl: String = ""): String = values(data, rule, baseUrl).joinToString("\n") {
        when (it) { is Element -> it.text(); else -> it.toString() }
    }.trim()

    fun values(data: Any?, raw: String, baseUrl: String = ""): List<Any> = try { evaluate(data, raw, baseUrl) }
    catch (error: StackOverflowError) { throw IllegalArgumentException("小说源正则或脚本递归过深，请更新书源。", error) }

    private fun evaluate(data: Any?, raw: String, baseUrl: String): List<Any> {
        var rule = raw.trim()
        val templated = rule.contains("{{")
        if (rule.isEmpty()) return emptyList()
        if (rule.contains("{{")) rule = template(rule, data, baseUrl)
        if (rule.startsWith("@js:") || rule.startsWith("<js>")) {
            val code = rule.removePrefix("@js:").removePrefix("<js>").removeSuffix("</js>")
            return normalize(script(code, data, baseUrl))
        }
        val scriptIndex = listOf(rule.indexOf("@js:"), rule.indexOf("<js>")).filter { it >= 0 }.minOrNull()
        if (scriptIndex != null) {
            val initial = text(data, rule.substring(0, scriptIndex), baseUrl)
            return normalize(script(rule.substring(scriptIndex).removePrefix("@js:").removePrefix("<js>").removeSuffix("</js>"), initial, baseUrl))
        }
        if (rule.contains("||")) {
            for (alternative in rule.split("||")) values(data, alternative, baseUrl).takeIf { it.isNotEmpty() && it.any { value -> value.toString().isNotBlank() } }?.let { return it }
            return emptyList()
        }
        if (rule.contains("&&")) return rule.split("&&").flatMap { values(data, it, baseUrl) }
        val replacements = rule.split("##")
        rule = replacements.first()
        val result = if (templated && !rule.startsWith("@") && !rule.startsWith("<js>")) listOf(rule) else select(data, rule, baseUrl)
        if (replacements.size == 1) return result
        return result.map { value -> replaceText(if (value is Element) value.text() else value.toString(), replacements[1], replacements.getOrElse(2) { "" }, replacements.size > 3) }
    }
    private fun normalize(value: Any?): List<Any> = when (value) {
        null, JSONObject.NULL -> emptyList()
        is List<*> -> value.filterNotNull().map { if (it is Map<*, *>) JSONObject(it) else it }
        is JSONArray -> List(value.length()) { value.get(it) }
        is String -> when {
            value.trim().startsWith('[') -> runCatching { normalize(JSONArray(value)) }.getOrDefault(listOf(value))
            else -> listOf(value)
        }
        else -> listOf(value)
    }
    private fun select(data: Any?, raw: String, baseUrl: String): List<Any> {
        if (raw.isEmpty()) return normalize(data)
        if (raw == "all") return normalize(data)
        if (raw.startsWith("https://") || raw.startsWith("http://")) return listOf(raw)
        val rule = raw.removePrefix("@json:")
        val json = data?.toString().orEmpty().trim()
        if (rule.startsWith('$') || json.startsWith('{') || json.startsWith('[')) {
            val path = if (rule.startsWith('$')) rule else "$.${rule}"
            return try { normalize(JsonPath.parse(json).read<Any?>(path)) } catch (_: PathNotFoundException) { emptyList() }
        }
        val root = data as? Element ?: Jsoup.parse(json, baseUrl)
        if (rule.startsWith("@XPath:") || rule.startsWith("//")) return root.selectXpath(rule.removePrefix("@XPath:")).toList()
        val parts = rule.removePrefix("@css:").removePrefix("@").split('@').filter { it.isNotBlank() }
        var current = listOf(root)
        for ((index, part) in parts.withIndex()) {
            if (index == parts.lastIndex && part in setOf("text", "ownText", "textNodes", "html", "outerHtml", "href", "src", "content", "value", "onclick", "data-src")) {
                return current.map { element -> when (part) {
                    "text" -> element.text()
                    "ownText" -> element.ownText()
                    "textNodes" -> element.textNodes().joinToString("\n") { it.text() }
                    "html" -> element.html()
                    "outerHtml" -> element.outerHtml()
                    else -> element.attr(part)
                } }.filter { it.isNotBlank() }
            }
            val exclude = part.substringAfter('!', "").split(',', ':').mapNotNull { it.toIntOrNull() }
            val selectorPart = part.substringBefore('!')
            val indexed = Regex("^(.*)\\.(-?\\d+)(?::(-?\\d+))?$").matchEntire(selectorPart)
            val selector = indexed?.groupValues?.get(1) ?: selectorPart
            val css = when {
                selector == "children" -> ":scope > *"
                selector.startsWith("class.") -> "." + selector.removePrefix("class.")
                selector.startsWith("id.") -> "#" + selector.removePrefix("id.")
                selector.startsWith("tag.") -> selector.removePrefix("tag.")
                selector.startsWith("text.") -> ":containsOwn(${selector.removePrefix("text.")})"
                else -> selector
            }
            current = current.flatMap { element ->
                val elements = if (selector == "children") element.children().toList() else element.select(css).toList()
                fun actual(i: Int) = if (i < 0) elements.size + i else i
                when {
                    indexed != null -> {
                        val first = actual(indexed.groupValues[2].toInt())
                        val last = indexed.groupValues[3].toIntOrNull()?.let(::actual) ?: first
                        elements.filterIndexed { i, _ -> i in first..last }
                    }
                    exclude.isNotEmpty() -> elements.filterIndexed { i, _ -> exclude.none { actual(it) == i } }
                    else -> elements
                }
            }
        }
        return current
    }
}
