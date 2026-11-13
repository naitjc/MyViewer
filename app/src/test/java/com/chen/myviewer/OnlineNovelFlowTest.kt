package com.chen.myviewer

import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.novel.NovelViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnlineNovelFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    @After fun cleanup() {
        val model = vm
        val app = model.getApplication<ViewerApplication>()
        model.closeReader()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
    }
    @Test fun importSearchReadResumeDownloadAndReadOfflineAfterUninstall() {
        val server = MockWebServer()
        server.start()
        val json = JSONObject(javaClass.getResourceAsStream("/legado/deqitxt.json")!!.bufferedReader().use { it.readText() })
            .put("bookSourceUrl", server.url("/").toString()).put("bookSourceName", "小说测试源")
            .put("searchUrl", server.url("/search").toString() + "?key={{key}}")
        val healthy = java.util.concurrent.atomic.AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath == "/search" && !healthy.get()) return MockResponse().setResponseCode(404)
                val body = when (request.requestUrl!!.encodedPath) {
                    "/sources" -> json.toString()
                    "/search" -> """<div class="container"><div class="item"><h3><a href="/book">原创联网小说</a></h3><p></p><p><a>测试作者</a></p></div></div>"""
                    "/book" -> """<div class="itemtxt"><h1><a>原创联网小说</a></h1></div><div class="des">原创新章节测试</div><ul id="list"><li><a href="/chapter/1">第一章 测试</a></li><li><a href="/chapter/2">第二章 测试</a></li></ul>"""
                    "/chapter/1", "/chapter/2" -> "<div class='con'><p>" + ("原创小说测试文字。" + request.requestUrl!!.encodedPath).repeat(400) + "</p></div>"
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body)
            }
        }
        try {
            val mangaSources = ViewModelProvider(compose.activity)[com.chen.myviewer.source.SourceViewModel::class.java]
            compose.runOnIdle { mangaSources.state.value = com.chen.myviewer.source.SourceBrowserState(catalog = listOf(com.chen.myviewer.source.SourceEntry("test", "测试", 1, listOf("zh"), "https://example.test/source.aix", "https://example.test", 0))) }
            compose.onNodeWithText("发现").performClick()
            compose.onNodeWithText("小说").performClick()
            waitForUi { compose.onAllNodesWithContentDescription("导入小说源").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("导入小说源").performClick()
            waitForUi { compose.onAllNodes(hasSetTextAction() and hasText("书源 JSON 地址")).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("整合书源").onLast().assertIsSelected()
            compose.onNodeWithText("XIU2 自用").performClick()
            compose.onNode(hasSetTextAction() and hasText("书源 JSON 地址")).assertTextContains("https://raw.githubusercontent.com/XIU2/Yuedu/master/shuyuan")
            capture("novel-personal-catalogs")
            compose.onNode(hasSetTextAction() and hasText("书源 JSON 地址")).performTextReplacement(server.url("/sources").toString())
            compose.onNodeWithText("导入并验证").performClick()
            val browser = ViewModelProvider(compose.activity)[NovelViewModel::class.java]
            waitForUi { browser.state.value.busy == null && browser.state.value.sources.any { it.name == "小说测试源" && it.check?.passed == false } }
            compose.onNodeWithText("小说测试源").assertDoesNotExist()
            compose.onNodeWithText("未通过").performClick()
            compose.onNodeWithText("HTTP 404", substring = true).assertExists()
            capture("novel-invalid-source")
            healthy.set(true)
            compose.onNodeWithContentDescription("验证 小说测试源").performClick()
            waitForUi { browser.state.value.busy == null && browser.state.value.sources.any { it.name == "小说测试源" && it.check?.passed == true } }
            compose.onNodeWithText("可用").performClick()
            capture("novel-validated-sources")
            waitForUi { compose.onAllNodesWithText("小说测试源").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("更新当前小说源仓库").assertExists()
            compose.onNodeWithText("全部已导入").performClick()
            compose.onNodeWithContentDescription("浏览小说源 小说测试源").performClick()
            compose.onNode(hasSetTextAction() and hasText("搜索小说")).performTextInput("原创")
            compose.onNodeWithContentDescription("搜索小说").performClick()
            waitForUi { compose.onAllNodesWithText("原创联网小说").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("原创联网小说").performClick()
            waitForUi { compose.onAllNodesWithText("章节 · 2").fetchSemanticsNodes().isNotEmpty() }
            capture("novel-online-details")
            val exportApp = vm.getApplication<ViewerApplication>()
            val exportProvider = TestDownloadProvider().register(exportApp)
            compose.onNodeWithText("下载整本到文件夹").performScrollTo().performClick()
            val folderRequest = shadowOf(compose.activity).nextStartedActivityForResult
            assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE, folderRequest.intent.action)
            compose.runOnIdle {
                compose.activity.activityResultRegistry.dispatchResult(folderRequest.requestCode, android.app.Activity.RESULT_CANCELED, null)
            }
            assertTrue(exportProvider.directory.listFiles()!!.isEmpty())
            compose.onNodeWithText("下载整本到文件夹").performClick()
            val selectedFolder = shadowOf(compose.activity).nextStartedActivityForResult
            compose.runOnIdle {
                compose.activity.activityResultRegistry.dispatchResult(selectedFolder.requestCode, android.app.Activity.RESULT_OK, android.content.Intent().setData(exportProvider.tree))
            }
            val folderDownloads = ViewModelProvider(compose.activity)[com.chen.myviewer.download.FolderDownloadViewModel::class.java]
            waitForUi { !folderDownloads.state.value.running && folderDownloads.state.value.saved == 1 }
            assertEquals(1, exportProvider.directory.listFiles()!!.size)
            capture("novel-folder-download")
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithContentDescription("保存小说章节 第一章 测试 到文件夹").performScrollTo().performClick()
            val singleChapterFolder = shadowOf(compose.activity).nextStartedActivityForResult
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                compose.activity.activityResultRegistry.dispatchResult(singleChapterFolder.requestCode, android.app.Activity.RESULT_OK, android.content.Intent().setData(exportProvider.tree))
            }
            waitForUi { !folderDownloads.state.value.running && folderDownloads.state.value.saved == 1 && exportProvider.directory.listFiles()!!.size == 2 }
            compose.onNodeWithText("关闭").performClick()
            assertTrue(exportProvider.directory.listFiles()!!.single { it.name.contains("第一章") }.readText().contains("第一章 测试"))
            compose.onNodeWithText("开始阅读").performScrollTo().performClick()
            waitForUi { vm.reader.value?.novelChapter != null }
            val tokenBeforeRefresh = vm.reader.value!!.position.sessionId
            compose.onRoot().performTouchInput { click(center) }
            compose.onNodeWithContentDescription("阅读设置").performClick()
            compose.onNodeWithText("重新加载正文").performScrollTo().performClick()
            waitForUi { vm.reader.value != null && vm.reader.value!!.position.sessionId != tokenBeforeRefresh }
            val model = vm
            val book = model.reader.value!!.book
            val title = model.reader.value!!.novelTitle!!
            compose.runOnIdle { model.seekText(1600) }
            waitForUi { model.reader.value!!.position.textOffset > 1000 }
            compose.runOnIdle { model.addBookmark() }
            waitForUi { runBlocking { model.repository.dao.chapterBookmarks(book.id, title.novel.chapters.first().url).first().isNotEmpty() } }
            compose.runOnIdle { model.closeReader() }
            waitForUi { model.reader.value == null && runBlocking { model.repository.dao.chapterPosition(book.id, title.novel.chapters.first().url) }?.textOffset?.let { it > 1000 } == true }
            compose.onNodeWithText("继续阅读").performClick()
            waitForUi { model.reader.value?.novelChapter != null }
            assertTrue(model.reader.value!!.position.textOffset > 1000)
            compose.onRoot().performTouchInput { click(center) }
            compose.onNodeWithText("下一章").assertIsEnabled()
            compose.mainClock.advanceTimeBy(300)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            compose.onNodeWithText("下一章").performClick()
            waitForUi { model.reader.value?.novelChapter?.url == title.novel.chapters.last().url || model.error.value != null }
            assertNull(model.error.value)
            assertEquals(0, model.reader.value!!.position.textOffset)
            assertTrue(runBlocking { model.repository.dao.chapterBookmarks(book.id, title.novel.chapters.last().url).first().isEmpty() })
            compose.runOnIdle { model.seekText(model.reader.value!!.text!!.text.lastIndex) }
            compose.waitForIdle()
            compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .9f, centerY)) }
            waitForUi { model.reader.value == null && runBlocking { model.repository.dao.chapterPosition(book.id, title.novel.chapters.last().url) }?.completed == true }
            assertFalse(runBlocking { model.repository.dao.book(book.id) }!!.completed)
            compose.onNodeWithText("离线下载全书").performClick()
            waitForUi { compose.onAllNodesWithText("应用内已下载 2 / 2 章").fetchSemanticsNodes().isNotEmpty() }
            capture("novel-online-downloaded")
            compose.onNodeWithContentDescription("返回小说源").performClick()
            compose.onNodeWithContentDescription("返回小说源").performClick()
            compose.onNodeWithText(com.chen.myviewer.novel.novelCatalogName(server.url("/sources").toString())).performClick()
            compose.onNodeWithContentDescription("删除小说源 小说测试源").performClick()
            compose.onNodeWithText("删除").performClick()
            val novels = ViewModelProvider(compose.activity)[NovelViewModel::class.java]
            waitForUi { novels.state.value.sources.none { it.id == title.sourceId } }
            server.shutdown()
            compose.onNodeWithText("书库").performClick()
            compose.onAllNodesWithText("原创联网小说").onLast().performClick()
            waitForUi { compose.onAllNodesWithText("继续阅读").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("第一章 测试").performClick()
            waitForUi { model.reader.value?.novelChapter?.url == title.novel.chapters.first().url }
            assertTrue(model.reader.value!!.position.textOffset > 1000)
            compose.runOnIdle { model.seekText(model.reader.value!!.text!!.text.lastIndex) }
            compose.waitForIdle()
            compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .9f, centerY)) }
            waitForUi { model.reader.value == null && model.library.value.first { it.book.id == book.id }.book.completed }
        } finally { server.close() }
    }
    private fun waitForUi(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(32)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30))
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("Timed out waiting for online novel UI reader=${vm.reader.value?.novelChapter} loading=${vm.loading.value?.title} error=${vm.error.value}", condition())
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull() ?: compose.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = File(System.getProperty("myviewer.outputDir"), "$name.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
}
