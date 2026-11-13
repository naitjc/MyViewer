package com.chen.myviewer

import android.app.Application
import androidx.room.Room
import com.chen.myviewer.data.ViewerDatabase
import com.chen.myviewer.novel.*
import com.chen.myviewer.source.SourceHttp
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NovelSourceTest {
    @Test fun realLegadoRulesSearchDetailsPaginationAndOfflineAfterSourceRemoval() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        var offline = false
        val paths = mutableListOf<String>()
        val http = SourceHttp(context, Interceptor { chain ->
            check(!offline) { "Offline reading must not make a network request" }
            val request = chain.request()
            paths += request.url.encodedPath
            val body = when (request.url.encodedPath) {
                "/so/so.aspx" -> """<div class="container"><div class="item"><a><img src="/cover.png"></a><h3><a href="/book/1">原创测试小说</a></h3><p>类别 <span>小说</span></p><p><a>测试作者</a></p></div></div>"""
                "/book/1" -> """<div class="itemtxt"><h1><a>原创测试小说</a></h1><p>类别 <span>小说</span></p><p><a>测试作者</a></p></div><div class="des">原创简介</div><ul id="list"><li><a href="/chapter/1">第一章</a></li><li><a href="/chapter/2">第二章</a></li></ul>"""
                "/chapter/1" -> """<div class="con"><p>原创第一段正文。</p></div><div class="prenext"><a href="/chapter/1_2">下一页</a></div>"""
                "/chapter/1_2" -> """<div class="con"><p>原创第二段正文。</p></div><div class="prenext"><a href="/chapter/2">下一页</a></div>"""
                "/chapter/2" -> """<div class="con"><p>原创第二章。</p></div>"""
                else -> error("Unexpected request ${request.url}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        })
        try {
            val repository = NovelRepository(context, db.dao(), http)
            val json = javaClass.getResourceAsStream("/legado/deqitxt.json")!!.bufferedReader().use { it.readText() }
            assertEquals(1, repository.importJson(json))
            assertEquals(1, repository.importJson(json))
            val source = repository.sources().single()
            val results = repository.search(source.id, "原创", 1)
            assertEquals("原创测试小说", results.single().name)
            val details = repository.details(source.id, results.single())
            assertEquals(2, details.chapters.size)
            val title = repository.saveTitle(source.id, details, true)
            val first = repository.loadChapter(title, details.chapters.first())
            assertTrue(first.text.contains("第一段"))
            assertTrue(first.text.contains("第二段"))
            assertFalse(first.text.contains("原创第二章"))
            assertFalse(paths.contains("/chapter/2"))
            val provider = TestDownloadProvider().register(context)
            val exports = com.chen.myviewer.download.FolderDownloadRepository(context, com.chen.myviewer.source.SourceRepository(context, db.dao()), repository)
            exports.novel(title, details.chapters, provider.tree) { _, _ -> }
            val exported = provider.directory.listFiles()!!.single().readText(Charsets.UTF_8)
            assertTrue(exported.startsWith("原创测试小说\n测试作者\n"))
            assertTrue(exported.indexOf("第一章") < exported.indexOf("第二章"))
            assertTrue(exported.contains("原创第二段正文"))
            repository.download(title, details.chapters) { _, _ -> }
            assertEquals(2, repository.downloaded(title).size)
            repository.uninstall(source.id)
            assertTrue(repository.sources().isEmpty())
            offline = true
            val reopened = NovelRepository(context, db.dao(), http)
            val saved = reopened.title(db.dao().book(title.book.id)!!)
            assertTrue(reopened.loadChapter(saved, details.chapters.last()).text.contains("原创第二章"))
            assertEquals(2, reopened.downloaded(saved).size)
            exports.novel(saved, listOf(details.chapters.last()), provider.tree) { _, _ -> }
            val chapterFile = provider.directory.listFiles()!!.single { it.name.contains("第二章") }
            assertTrue(chapterFile.readText().contains("原创第二章"))
            assertFalse(chapterFile.readText().contains("第一章"))
            reopened.deleteDownloads(saved)
            assertTrue(reopened.downloaded(saved).isEmpty())
        } finally { db.close() }
    }

    @Test fun secondUpstreamSourceKeepsHtmlParagraphsAndStopsBeforeTheNextChapter() {
        val context = RuntimeEnvironment.getApplication()
        val source = NovelSource(JSONObject(javaClass.getResourceAsStream("/legado/xyetianlian.json")!!.bufferedReader().use { it.readText() }))
        var contentRequests = 0
        val http = SourceHttp(context, Interceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/s.php" -> """<div class="bookbox"><div class="bookname"><a href="/book">原创小说</a></div><div class="bookinfo"><div class="author">作者</div></div></div>"""
                "/book" -> """<div class="info"><h2>原创小说</h2></div><div class="listmain"><dd><a href="/one">第一章</a></dd><dd><a href="/two">第二章</a></dd></div>"""
                "/one" -> { contentRequests++; """<div class="showtxt">第一段<br>第二段</div><a href="/two">下一章</a>""" }
                else -> error("Must not read the next chapter as a page")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        })
        val engine = LegadoEngine(source, http)
        val book = engine.details(engine.search("原创", 1).single())
        val text = engine.content(book, book.chapters.first())
        assertEquals(listOf("第一段", "第二段"), text.lines().map { it.trim() }.filter { it.isNotEmpty() })
        assertEquals(1, contentRequests)
    }

    @Test fun jsonPathsScriptsTemplatesAndIndexRulesUseTheSameFormatAsImportedSources() {
        val rules = LegadoRules(mapOf("key" to "红楼梦", "page" to 3)) { "original" }
        val json = """{"data":[{"name":"原创","id":17},{"name":"下一本","id":18}]}"""
        val items = rules.values(json, "$.data[*]")
        assertEquals("原创", rules.text(items.first(), "$.name"))
        assertEquals("https://example.test/17", rules.text(items.first(), "https://example.test/{{$.id}}"))
        assertEquals("page=2", rules.template("page={{page-1}}", "", ""))
        assertEquals("ORIGINAL", rules.text("", "@js: java.ajax('https://example.test').toUpperCase()"))
        val html = """<table class="grid"><tbody><tr><td>表头</td></tr><tr><td class="even"><a href="/17">测试</a></td><td class="even">作者</td></tr></tbody></table>"""
        val rows = rules.values(html, "class.grid@tag.tbody@tag.tr!0")
        assertEquals(1, rows.size)
        assertEquals("作者", rules.text(rows.single(), "class.even.1@text"))
        assertEquals("17", rules.text(rows.single(), "class.even.0@tag.a@href<js>result.match(/(\\d+)/)[1]</js>"))
        assertEquals("测试", rules.text(rows.single(), "class.even.0@text##测试##测试"))
        try { rules.text("", "@js:java.getClass().forName('java.lang.Runtime')"); fail("Java reflection must be unavailable") }
        catch (_: Exception) { }
    }
    @Test fun templatePatternUsesIcuSafeDelimitersAndRendersRequestExpressions() {
        assertTrue(LegadoRules.TEMPLATE_PATTERN.pattern.endsWith("\\}\\}"))
        val rules = LegadoRules(mapOf("page" to 3)) { "" }
        assertEquals("p=2&next=3", rules.template("p={{page-1}}&next={{page}}", "", ""))
        assertEquals("前文😀后文", rules.template("前文{{'😀'}}后文", "", ""))
        assertEquals("keep {single} braces", rules.template("keep {single} braces", "", ""))
    }

    @Test fun personalCatalogImportsRetainOriginAndExcludePreviewChapterIndexes() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        val json = javaClass.getResourceAsStream("/legado/aixiashu.json")!!.bufferedReader().use { it.readText() }
        val http = SourceHttp(context, Interceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/speauty/legado.sources/main/subscriptions/active.json" -> json
                "/modules/article/search.php" -> """<table class="grid"><tbody><tr><th>书名</th></tr><tr><td class="even"><a href="/book">原创个人源测试</a></td><td class="even">测试作者</td></tr></tbody></table>"""
                "/book" -> """<div id="info"><h1>原创个人源测试</h1><p>作    者：测试作者</p></div><div id="list"><dl>""" +
                    """<dd><a href="/two">最新 第二章</a></dd>""".repeat(9) + """<dd><a href="/one">第一章</a></dd><dd><a href="/two">第二章</a></dd></dl></div>"""
                "/one" -> """<div id="content">原创第一段。<br>原创第二段。</div>"""
                "/two" -> """<div id="content">原创第二章。</div>"""
                else -> error("Unexpected request ${request.url}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        })
        try {
            val repository = NovelRepository(context, database.dao(), http)
            assertEquals(1, repository.importUrl(NOVEL_SOURCE_URL))
            assertEquals(NOVEL_SOURCE_URL, repository.sources().single().catalog)
            repository.setCatalogFilter(NOVEL_SOURCE_URL)
            assertEquals(NOVEL_SOURCE_URL, NovelRepository(context, database.dao(), http).catalogFilter)
            repository.importJson(json)
            val source = repository.sources().single()
            assertEquals(NOVEL_SOURCE_URL, source.catalog)
            val result = repository.search(source.id, "原创", 1).single()
            val book = repository.details(source.id, result)
            assertEquals(listOf("第一章", "第二章"), book.chapters.map { it.name })
            val title = repository.saveTitle(source.id, book)
            assertTrue(repository.loadChapter(title, book.chapters.first()).text.contains("原创第二段"))
            repository.importJson(json, "https://example.test/alternate.json")
            assertEquals("https://example.test/alternate.json", repository.sources().single().catalog)
            assertEquals(title.book.id, repository.saveTitle(source.id, book).book.id)
        } finally { database.close() }
    }

}
