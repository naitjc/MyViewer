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
    private val stored = mutableMapOf<String, String>()
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
            fun put(name: String, value: Any?) {
                val js = if (value is JSONObject || value is JSONArray) cx.evaluateString(scope, "JSON.parse(${JSONObject.quote(value.toString())})", "novel-variable", 1, null) else Context.javaToJS(value, scope)
                ScriptableObject.putProperty(scope, name, js)
            }
            fun function(action: (Array<out Any>) -> Any?): BaseFunction = object : BaseFunction() {
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any? { checkActive(); return action(args) }
            }
            variables.forEach(::put)
            put("baseUrl", baseUrl)
            val input = when (result) { is Element -> result.outerHtml(); else -> result?.toString().orEmpty() }
            put("result", input)
            val bridge = cx.newObject(scope)
            ScriptableObject.putProperty(bridge, "ajax", function { ajax(Context.toString(it.firstOrNull() ?: "")) })
            ScriptableObject.putProperty(bridge, "getString", function { text(result, Context.toString(it.firstOrNull() ?: ""), baseUrl) })
            ScriptableObject.putProperty(bridge, "getStringList", function { cx.newArray(scope, values(result, Context.toString(it.firstOrNull() ?: ""), baseUrl).map { value -> if (value is Element) value.text() else value.toString() }.toTypedArray()) })
            ScriptableObject.putProperty(bridge, "get", function { stored[Context.toString(it.firstOrNull() ?: "")] ?: "" })
            ScriptableObject.putProperty(bridge, "put", function { stored[Context.toString(it[0])] = Context.toString(it[1]); it[1] })
            ScriptableObject.putProperty(scope, "java", bridge)
            val value = cx.evaluateString(scope, code, "novel-source", 1, null)
            when (value) {
                null, Undefined.instance -> ""
                is Scriptable -> Context.toString(cx.evaluateString(scope.apply { ScriptableObject.putProperty(this, "__output", value) }, "JSON.stringify(__output)", "novel-result", 1, null))
                else -> Context.toString(value)
            }
        })
    }
    fun template(rule: String, data: Any?, baseUrl: String): String = Regex("\\{\\{([\\s\\S]*?)}}").replace(rule) { match ->
        val expression = match.groupValues[1].trim()
        if (expression.startsWith('$') || expression.startsWith('@')) text(data, expression.removePrefix("@"), baseUrl)
        else script(expression, data, baseUrl).toString()
    }
    fun text(data: Any?, rule: String, baseUrl: String = ""): String = values(data, rule, baseUrl).joinToString("\n") {
        when (it) { is Element -> it.text(); else -> it.toString() }
    }.trim()

    fun values(data: Any?, raw: String, baseUrl: String = ""): List<Any> {
        var rule = raw.trim()
        if (rule.isEmpty()) return emptyList()
        if (rule.contains("{{")) return listOf(template(rule, data, baseUrl))
        if (rule.startsWith("@js:") || rule.startsWith("<js>")) {
            val code = rule.removePrefix("@js:").removePrefix("<js>").removeSuffix("</js>")
            return normalize(script(code, data, baseUrl))
        }
        val scriptIndex = listOf(rule.indexOf("@js:"), rule.indexOf("<js>")).filter { it >= 0 }.minOrNull()
        if (scriptIndex != null) {
            val initial = text(data, rule.substring(0, scriptIndex), baseUrl)
            return values(initial, rule.substring(scriptIndex), baseUrl)
        }
        if (rule.contains("||")) {
            for (alternative in rule.split("||")) values(data, alternative, baseUrl).takeIf { it.isNotEmpty() && it.any { value -> value.toString().isNotBlank() } }?.let { return it }
            return emptyList()
        }
        if (rule.contains("&&")) return rule.split("&&").flatMap { values(data, it, baseUrl) }
        val replacements = rule.split("##", limit = 3)
        rule = replacements.first()
        val result = select(data, rule, baseUrl)
        if (replacements.size == 1) return result
        return result.map { value -> Regex(replacements[1]).replace(if (value is Element) value.text() else value.toString(), replacements.getOrElse(2) { "" }) }
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
            val exclude = part.substringAfter('!', "").split(',').mapNotNull { it.toIntOrNull() }
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
