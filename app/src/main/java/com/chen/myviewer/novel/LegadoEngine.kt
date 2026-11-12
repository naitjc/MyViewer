package com.chen.myviewer.novel

import com.chen.myviewer.source.SourceHttp
import org.json.JSONObject
import org.jsoup.Jsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale

internal data class NovelResponse(val url: String, val body: String)

internal class LegadoEngine(private val source: NovelSource, private val http: SourceHttp) {
    private var key = ""
    private var page = 1
    private val variables = mutableMapOf<String, Any>("key" to "", "page" to 1, "source" to source.json, "book" to JSONObject())
    private fun rules(): LegadoRules = LegadoRules(variables) { request(it, source.baseUrl).body }
    private val analyzer by lazy(::rules)

    fun absolute(value: String, base: String): String {
        if (value.isBlank()) return ""
        val resolved = value.trim().toHttpUrlOrNull()?.toString() ?: base.toHttpUrlOrNull()?.resolve(value.trim())?.toString() ?: error("小说源返回了无效的网页地址。")
        require(resolved.startsWith("https://") || resolved.startsWith("http://")) { "小说源返回了无效的网页地址。" }
        return resolved
    }
    private fun headers(): Map<String, String> {
        var value = source.json.optString("header")
        if (value.isBlank()) return emptyMap()
        if (value.startsWith("@js:") || value.startsWith("<js>")) value = analyzer.text("", value, source.baseUrl)
        val json = JSONObject(value)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
    fun request(raw: String, base: String, data: Any? = ""): NovelResponse {
        require(raw.isNotBlank()) { "小说源缺少请求地址。" }
        if (Thread.currentThread().isInterrupted) throw InterruptedException("操作已取消")
        var specification = raw
        val charsetName = Regex("[\"']charset[\"']\\s*:\\s*[\"']([^\"']+)").find(raw)?.groupValues?.get(1) ?: "UTF-8"
        specification = specification.replace("{{key}}", URLEncoder.encode(key, charsetName))
        specification = analyzer.template(specification, data, base)
        if (specification.startsWith("@js:") || specification.startsWith("<js>")) specification = analyzer.text(data, specification, base)
        val separator = Regex(",\\s*\\{").find(specification)
        val options = separator?.let { JSONObject(specification.substring(it.range.first + 1)) } ?: JSONObject()
        require(!options.optBoolean("webView") && !options.has("webJs")) { "这个小说源需要浏览器验证，当前尚未支持。" }
        val url = absolute(separator?.let { specification.substring(0, it.range.first) } ?: specification, base)
        val requestHeaders = headers().toMutableMap()
        val custom = options.optJSONObject("headers")
        custom?.keys()?.forEach { requestHeaders[it] = custom.getString(it) }
        val charset = Charset.forName(options.optString("charset", "UTF-8"))
        val method = options.optString("method", "GET").uppercase(Locale.ROOT)
        val body = options.opt("body")?.takeIf { it != JSONObject.NULL }?.toString()?.toByteArray(charset)
        if (body != null && requestHeaders.keys.none { it.equals("Content-Type", true) }) requestHeaders["Content-Type"] = "application/x-www-form-urlencoded; charset=${charset.name()}"
        val response = http.request(url, requestHeaders, method, body)
        require(response.status in 200..299) { "小说网页请求失败（HTTP ${response.status}）。" }
        val contentType = response.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value.orEmpty()
        val detected = Regex("charset=[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)
            ?: Regex("charset=[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(response.data.take(4096).toByteArray().toString(Charsets.ISO_8859_1))?.groupValues?.get(1)
        return NovelResponse(response.url, response.data.toString(if (options.has("charset")) charset else detected?.let(Charset::forName) ?: Charsets.UTF_8))
    }
    fun search(keyword: String, pageNumber: Int): List<NovelBook> {
        key = keyword
        page = pageNumber
        variables["key"] = key
        variables["page"] = page
        val response = request(source.json.optString("searchUrl"), source.baseUrl)
        val rule = source.rule("ruleSearch")
        return analyzer.values(response.body, rule.optString("bookList"), response.url).mapNotNull { item ->
            val name = analyzer.text(item, rule.optString("name"), response.url)
            val url = analyzer.text(item, rule.optString("bookUrl"), response.url)
            if (name.isBlank() || url.isBlank()) null else NovelBook(absolute(url, response.url), name,
                analyzer.text(item, rule.optString("author"), response.url),
                absolute(analyzer.text(item, rule.optString("coverUrl"), response.url), response.url),
                analyzer.text(item, rule.optString("intro"), response.url))
        }.distinctBy { it.url }.take(1000)
    }
    fun details(novel: NovelBook): NovelBook {
        variables["book"] = novel.json().put("bookUrl", novel.url).put("origin", source.url)
        val response = request(novel.url, source.baseUrl)
        val info = source.rule("ruleBookInfo")
        val body = info.optString("init").takeIf { it.isNotBlank() }?.let { analyzer.values(response.body, it, response.url).firstOrNull() ?: response.body } ?: response.body
        fun value(name: String) = analyzer.text(body, info.optString(name), response.url)
        val tocUrl = value("tocUrl").ifBlank { novel.tocUrl }
        var result = novel.copy(name = value("name").ifBlank { novel.name }, author = value("author").ifBlank { novel.author },
            cover = value("coverUrl").takeIf { it.isNotBlank() }?.let { absolute(it, response.url) } ?: novel.cover,
            intro = value("intro").ifBlank { novel.intro }, tocUrl = absolute(tocUrl, response.url))
        val chapters = mutableListOf<NovelChapter>()
        val visited = mutableSetOf<String>()
        val tocRule = source.rule("ruleToc")
        var current = if (result.tocUrl == response.url || result.tocUrl == novel.url) response else request(result.tocUrl, response.url)
        val reverse = tocRule.optString("chapterList").startsWith('-')
        while (visited.add(current.url)) {
            require(visited.size <= 200) { "目录分页超过支持上限。" }
            val items = analyzer.values(current.body, tocRule.optString("chapterList").removePrefix("-").removePrefix("+"), current.url)
            for (item in items) {
                val name = analyzer.text(item, tocRule.optString("chapterName"), current.url)
                val url = analyzer.text(item, tocRule.optString("chapterUrl"), current.url)
                val vip = analyzer.text(item, tocRule.optString("isVip"), current.url)
                if (name.isNotBlank() && url.isNotBlank()) chapters += NovelChapter(name, absolute(url, current.url), vip == "true" || vip == "1")
            }
            require(chapters.size <= 100_000) { "章节数量超过支持上限。" }
            val next = analyzer.text(current.body, tocRule.optString("nextTocUrl"), current.url).lineSequence().firstOrNull().orEmpty()
            if (next.isBlank()) break
            val nextUrl = absolute(next, current.url)
            if (nextUrl in visited) break
            current = request(nextUrl, current.url)
        }
        val unique = chapters.distinctBy { it.url }.let { if (reverse) it.asReversed() else it }
        require(unique.isNotEmpty()) { "没有解析到小说章节，请检查书源规则或站点状态。" }
        result = result.copy(chapters = unique)
        return result
    }
    fun content(novel: NovelBook, chapter: NovelChapter): String {
        require(!chapter.locked) { "该章节需要站点授权。" }
        variables["book"] = novel.json().put("bookUrl", novel.url).put("origin", source.url).put("durChapterTitle", chapter.name)
        val rule = source.rule("ruleContent")
        val pages = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val otherChapters = novel.chapters.map { it.url }.toSet() - chapter.url
        var current = request(chapter.url, source.baseUrl)
        while (visited.add(current.url)) {
            require(visited.size <= 30) { "正文分页超过支持上限。" }
            val content = analyzer.text(current.body, rule.optString("content"), current.url)
            require(content.isNotBlank()) { "没有解析到章节正文，请检查书源或完成站点授权。" }
            val cleaned = Jsoup.parse(content.replace(Regex("(?i)<br\\s*/?>|</p>|</div>"), "\n")).apply { select("script,style,noscript").remove() }.wholeText().replace('\u00a0', ' ').trim()
            pages += cleaned
            require(pages.sumOf { it.length } <= 2_000_000) { "章节正文超过支持上限。" }
            val next = analyzer.text(current.body, rule.optString("nextContentUrl"), current.url).lineSequence().firstOrNull().orEmpty()
            if (next.isBlank()) break
            val nextUrl = absolute(next, current.url)
            if (nextUrl in visited || nextUrl in otherChapters) break
            current = request(nextUrl, current.url)
        }
        var text = pages.joinToString("\n\n")
        val replace = rule.optString("replaceRegex")
        if (replace.isNotBlank()) for (entry in replace.split("&&")) {
            val parts = entry.split("##", limit = 2)
            text = Regex(parts[0]).replace(text, parts.getOrElse(1) { "" })
        }
        require(text.isNotBlank()) { "该章节没有可阅读的正文。" }
        return text
    }
}
