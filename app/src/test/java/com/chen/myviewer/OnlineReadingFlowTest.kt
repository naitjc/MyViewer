package com.chen.myviewer

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.source.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
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
class OnlineReadingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun clean() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        vm.closeReader()
        val app = vm.getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
    }

    @Test fun shelfOpensOfflineChaptersAndRestoresEachChapterAfterSwitching() {
        compose.onNodeWithText("书库").performClick()
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val app = vm.getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
        val chapters = listOf(SourceChapter("two", "第二章", 2f, false, byteArrayOf(0)), SourceChapter("one", "第一章", 1f, false, byteArrayOf(0)))
        val manga = SourceManga("offline-test", "离线漫画测试", null, listOf("测试作者"), "原创离线阅读测试。", null, emptyList(), 1, chapters, byteArrayOf(0))
        val title = runBlocking { app.sources.saveTitle("test.offline", manga) }
        chapters.forEach { chapter ->
            val directory = app.sources.chapterDirectory(title.book.id, chapter.key).apply { mkdirs() }
            repeat(3) { index ->
                val image = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
                image.eraseColor(if (index % 2 == 0) android.graphics.Color.rgb(180, 200, 160) else android.graphics.Color.rgb(150, 180, 210))
                File(directory, "$index.img").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            File(directory, "pages.json").writeText(JSONArray((0..2).map { JSONObject().put("url", "https://offline.invalid/$it.png") }).toString())
        }
        waitForUi { compose.onAllNodesWithText("离线漫画测试").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("离线漫画测试").onFirst().performClick()
        waitForUi { compose.onAllNodesWithText("开始阅读").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("章节 · 2").assertExists()
        capture("online-offline-chapters")
        compose.onNodeWithText("标记已读完").performClick()
        waitForUi { vm.library.value.firstOrNull { it.book.id == title.book.id }?.book?.completed == true }
        compose.onNodeWithText("取消已读完标记").performClick()
        compose.onNodeWithContentDescription("章节操作：${chapters.last().label}").performClick()
        compose.onAllNodesWithText("标记已读完").onLast().performClick()
        waitForUi { runBlocking { vm.repository.dao.chapterPosition(title.book.id, "one") }?.completed == true }
        capture("online-chapter-completed")
        compose.onNodeWithText("开始阅读").performClick()
        waitForUi { vm.reader.value?.chapter?.key == "one" && vm.reader.value?.comic?.cachedPage(0) != null }
        compose.runOnIdle { vm.seekComic(1); vm.flushPosition() }
        compose.onRoot().performTouchInput { click(center) }
        waitForUi { compose.onAllNodesWithText("下一章").fetchSemanticsNodes().isNotEmpty() }
        capture("online-reader-chapter-controls")
        compose.onNodeWithText("下一章").performClick()
        waitForUi { vm.reader.value?.chapter?.key == "two" }
        assertEquals(0, vm.reader.value!!.position.imageIndex)
        compose.runOnIdle { vm.seekComic(2); vm.flushPosition(); vm.open(title.book, title, chapters.last()) }
        waitForUi { vm.reader.value?.chapter?.key == "one" }
        assertEquals(1, vm.reader.value!!.position.imageIndex)
        compose.runOnIdle { vm.closeReader() }
        waitForUi { vm.reader.value == null && compose.onAllNodesWithText("继续阅读").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("继续阅读").performClick()
        waitForUi { vm.reader.value?.chapter?.key == "one" }
        assertEquals(1, vm.reader.value!!.position.imageIndex)
        assertEquals(2, runBlocking { vm.repository.dao.chapterPosition(title.book.id, "two") }!!.imageIndex)
        assertNull(vm.error.value)
        assertEquals(true, runBlocking { vm.repository.dao.chapterPosition(title.book.id, "one") }!!.completed)
        compose.runOnIdle { vm.closeReader() }
        compose.onNodeWithText("书库").performClick()
        waitForUi { compose.onAllNodesWithText("在线漫画", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun sourceCatalogFiltersLanguagesAndOpensInstalledSourceSettings() {
        val sources = ViewModelProvider(compose.activity)[SourceViewModel::class.java]
        val app = sources.getApplication<ViewerApplication>()
        val guya = SourceEntry("en.guya", "Guya", 2, listOf("en"), "https://example.test/guya.aix", "https://guya.cubari.moe", 0)
        val copy = SourceEntry("zh.copymanga", "拷贝漫画", 24, listOf("zh"), "https://example.test/copy.aix", "https://www.copy5000.com", 1)
        val japanese = guya.copy(id = "ja.test", name = "日文源", languages = listOf("ja"))
        val portuguese = guya.copy(id = "pt.test", name = "葡文源", languages = listOf("pt-BR"))
        val traditional = copy.copy(id = "zh.traditional", name = "繁体源", languages = listOf("zh-Hant"))
        val installed = app.sources.installPackage(guya, javaClass.getResourceAsStream("/aidoku/en.guya-v2.aix")!!.use { it.readBytes() })
        compose.runOnIdle { sources.state.value = SourceBrowserState(catalog = listOf(guya, copy, japanese, portuguese, traditional), installed = listOf(installed)) }
        compose.onNodeWithText("发现").performClick()
        waitForUi { compose.onAllNodesWithText("Guya").fetchSemanticsNodes().isNotEmpty() }
        capture("online-source-catalog")
        compose.onNodeWithText("中文").performClick()
        compose.onNodeWithText("拷贝漫画").assertExists()
        compose.onNodeWithText("Guya").assertDoesNotExist()
        compose.onNodeWithText("繁体源").assertExists()
        compose.onNodeWithText(sourceLanguageLabel("en")).performClick()
        compose.onNodeWithText("Guya").assertExists()
        compose.onNodeWithText("拷贝漫画").assertDoesNotExist()
        compose.onNodeWithText(sourceLanguageLabel("ja")).performClick()
        compose.onNodeWithText("日文源").assertExists()
        compose.onNodeWithText("更多语言").performScrollTo().performClick()
        compose.onNodeWithText(sourceLanguageLabel("pt")).performClick()
        compose.onNodeWithText("葡文源").assertExists()
        compose.onNodeWithText("Guya").assertDoesNotExist()
        capture("source-languages-portuguese")
        compose.runOnIdle { sources.state.value = sources.state.value.copy(source = guya) }
        compose.onNodeWithContentDescription("漫画源设置").performClick()
        waitForUi { compose.onAllNodesWithText("站点地址", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        capture("online-source-settings")
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { sources.back() }
        compose.onNodeWithText("葡文源").assertExists()
        assertEquals("pt", sources.state.value.language)
    }
    private fun waitForUi(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30))
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("Timed out waiting for UI", condition())
    }
    private fun capture(name: String) {
        val file = File(System.getProperty("myviewer.outputDir"), "$name.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
}
