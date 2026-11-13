package com.chen.myviewer

import android.app.Application
import androidx.room.Room
import com.chen.myviewer.data.ViewerDatabase
import com.chen.myviewer.novel.*
import com.chen.myviewer.source.SourceHttp
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NovelValidationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var database: ViewerDatabase
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build() }
    @After fun cleanup() { database.close() }

    private fun definition(host: String = "sample.test") = JSONObject().put("bookSourceName", "原创验证源").put("bookSourceUrl", "https://$host")
        .put("searchUrl", "/search?key={{key}}")
        .put("ruleSearch", JSONObject().put("bookList", "li").put("name", "a@text").put("bookUrl", "a@href"))
        .put("ruleBookInfo", JSONObject().put("name", "h1@text"))
        .put("ruleToc", JSONObject().put("chapterList", "a.chapter").put("chapterName", "text").put("chapterUrl", "href"))
        .put("ruleContent", JSONObject().put("content", "#content@html").put("replaceRegex", "##广告标记"))

    private fun body(path: String): String = when (path) {
        "/search" -> "<li><a href='/book'>原创小说</a></li>"
        "/book" -> "<h1>原创小说</h1><a class='chapter' href='/one'>第一章</a><a class='chapter' href='/two'>第二章</a>"
        else -> "<div id='content'>${("原创${if (path == "/one") "第一" else "第二"}章测试文字。\n").repeat(40)}</div>"
    }

    private fun response(chain: Interceptor.Chain, status: Int = 200): Response = Response.Builder().request(chain.request())
        .protocol(Protocol.HTTP_1_1).code(status).message("Test").header("Content-Type", "text/html; charset=GBK")
        .body(body(chain.request().url.encodedPath).toByteArray(Charset.forName("GB18030")).toResponseBody()).build()

    @Test fun domBridgeReturnsElementsAndSrcRetainsFullPageDuringChainedRules() {
        val html = "<main data-parent='yes'><a href='/one'>第一项</a><a href='/two'>第二项</a></main>"
        val rules = LegadoRules(mapOf("book" to JSONObject().put("bookUrl", "https://example.test/book"))) { "" }
        rules.setContent(html)
        val elements = rules.values(html, "@js:java.getElement('a')")
        assertEquals(listOf("第一项", "第二项"), elements.map { (it as Element).text() })
        assertEquals("/one", rules.text(html, "@js:java.getElement('a').get(0).attr('href')"))
        assertEquals("第二项", rules.text(html, "a.0@text@js:java.getString('a.1@text')"))
        assertEquals("true", rules.text("已选择的内容", "@js:src.includes('data-parent') && result === '已选择的内容'"))
        assertEquals("/17", rules.text("prefix id=17 suffix", "##id=(\\d+)##/$1###"))
        assertEquals("https://example.test/book", rules.text(html, "@js:book.getBookUrl()"))
        assertEquals("更新后", rules.text(html, "@js:java.setContent('<p>更新后</p>'); java.getElement('p').first().text()"))
        try { rules.script("java.getElement('p').first().getClass()", html); fail("DOM reflection must remain unavailable") }
        catch (_: Exception) { }
    }

    @Test fun webpageDecoderHandlesGbWithoutMetadataAndDoesNotForceRequestCharsetOnUtf8() {
        val text = "原创小说第一章，春雨停了。"
        assertEquals(text, NovelTextDecoder.decode(text.toByteArray(Charset.forName("GB18030")), "text/html"))
        assertEquals(text, NovelTextDecoder.decode(text.toByteArray(), "text/html; charset=GBK", "GBK"))
        val gbHtml = "<meta charset='gb2312'>$text"
        assertEquals(gbHtml, NovelTextDecoder.decode(gbHtml.toByteArray(Charset.forName("GB18030")), "text/html; charset=UTF-8"))
        assertEquals(text, NovelTextDecoder.decode(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE), "text/html"))
        try { NovelTextDecoder.decode(byteArrayOf(0xff.toByte()), "text/html"); fail("Invalid bytes must fail explicitly") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("解码失败")) }
    }

    @Test fun integratedBundleContainsOnlyPassingUniqueOriginsAndDeletionSurvivesReinitialization() = runBlocking {
        val repository = NovelRepository(context, database.dao())
        repository.installIntegrated()
        val sources = repository.sources()
        assertTrue(sources.isNotEmpty())
        assertTrue(sources.all { it.check?.passed == true })
        assertEquals(sources.groupBy { it.originKey }.filterValues { it.size > 1 }.mapValues { it.value.map { s -> s.url } }.toString(), sources.size, sources.map { it.originKey }.distinct().size)
        assertEquals(INTEGRATED_NOVEL_CATALOG, repository.catalogFilter)
        repository.uninstall(sources.first().id)
        repository.installIntegrated()
        assertEquals(sources.size - 1, repository.sources().size)
    }

    @Test fun validationRecordsStagesAndHttp404WhileOfflineContentRemainsReadable() = runBlocking {
        var failing = false
        val http = SourceHttp(context, Interceptor { response(it, if (failing) 404 else 200) })
        val repository = NovelRepository(context, database.dao(), http)
        repository.importJson(definition().toString())
        var source = repository.sources().single()
        repository.validate(listOf(source)) { _, _, _ -> }
        source = repository.sources().single()
        assertTrue(source.check!!.passed)
        assertEquals(2, source.check!!.samples)
        val title = repository.saveTitle(source.id, repository.details(source.id, repository.search(source.id, "原创", 1).first()))
        repository.download(title, title.novel.chapters) { _, _ -> }
        failing = true
        repository.validate(listOf(source)) { _, _, _ -> }
        val failed = repository.sources().single().check!!
        assertFalse(failed.passed)
        assertEquals("搜索", failed.stage)
        assertTrue(failed.message.contains("404"))
        assertTrue(repository.loadChapter(title, title.novel.chapters.first()).text.contains("原创第一章"))
        assertEquals(title.book.id, database.dao().book(title.book.id)!!.id)
        failing = false
        repository.refreshChapter(title, title.novel.chapters.first())
        assertTrue(repository.loadChapter(title, title.novel.chapters.first()).text.contains("原创第一章"))
    }

    @Test fun cancelledValidationPersistsCompletedChecksWithoutRestoringAnUninstalledSource() = runBlocking {
        val slowEntered = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val http = SourceHttp(context, Interceptor { chain ->
            if (chain.request().url.host == "slow.test") { slowEntered.countDown(); CountDownLatch(1).await(20, TimeUnit.SECONDS) }
            response(chain)
        })
        val repository = NovelRepository(context, database.dao(), http)
        repository.importJson("[${definition()},${definition("slow.test")}]")
        val initial = repository.sources()
        val job = launch { repository.validate(initial) { _, _, _ -> completed.countDown() } }
        withContext(Dispatchers.IO) {
            assertTrue(slowEntered.await(5, TimeUnit.SECONDS))
            assertTrue(completed.await(5, TimeUnit.SECONDS))
        }
        repository.uninstall(initial.first { it.baseUrl.contains("slow.test") }.id)
        job.cancelAndJoin()
        assertEquals(1, repository.sources().size)
        assertTrue(repository.sources().single().check!!.passed)
    }
    @Test fun repeatedContentAcrossChaptersFailsValidation() = runBlocking {
        val http = SourceHttp(context, Interceptor { chain ->
            if (chain.request().url.encodedPath == "/two") response(chain).newBuilder().body(body("/one").toByteArray(Charset.forName("GB18030")).toResponseBody()).build()
            else response(chain)
        })
        val result = NovelSourceValidator(http).check(NovelSource(definition()))
        assertFalse(result.passed)
        assertEquals("正文", result.stage)
        assertTrue(result.message.contains("相同"))
    }

    @Test fun importedValidationClaimsAreDiscardedAndRuleChangesRequireNewChecks() = runBlocking {
        val http = SourceHttp(context, Interceptor { response(it) })
        val repository = NovelRepository(context, database.dao(), http)
        val source = NovelSource(definition())
        source.json.put("_myviewerCheck", NovelSourceCheck(source.fingerprint, true, "完成", "外部文件声称通过").json())
        repository.importJson(source.json.toString())
        assertNull(repository.sources().single().check)
        repository.validate(repository.sources()) { _, _, _ -> }
        assertTrue(repository.sources().single().check!!.passed)
        val changed = definition().put("searchUrl", "/missing")
        repository.importJson(changed.toString())
        assertNull(repository.sources().single().check)
    }

    @Test fun shortSingleChapterCanPassTheSameReaderValidation() = runBlocking {
        val http = SourceHttp(context, Interceptor { chain ->
            val text = when (chain.request().url.encodedPath) {
                "/book" -> "<h1>原创小说</h1><a class='chapter' href='/one'>第一章</a>"
                "/one" -> "<div id='content'>雨</div>"
                else -> body(chain.request().url.encodedPath)
            }
            response(chain).newBuilder().body(text.toByteArray(Charset.forName("GB18030")).toResponseBody()).build()
        })
        val result = NovelSourceValidator(http).check(NovelSource(definition()))
        assertTrue(result.passed)
        assertEquals(1, result.samples)
    }

}
