package com.chen.myviewer

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryPagesFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Before fun reset() {
        val app = vm.getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables(); app.settings.setLibrarySort(LibrarySort.RECENT)
            app.settings.setLibraryColumns(3); app.settings.update(ReaderSettings())
        }
    }
    @After fun clean() {
        val app = vm.getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables(); app.settings.update(ReaderSettings()); app.settings.setLibraryColumns(3) }
    }

    @Test fun historyGroupsDatesClearsOnlyHistoryAndSettingsRemainUsable() {
        seedHistory()
        compose.onNodeWithText("历史").performClick()
        waitForUi { compose.onNodeWithText("阅读记录").isDisplayed() }
        compose.onNodeWithText("今天").assertExists()
        capture("history-redesign")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("昨天"))
        compose.onNodeWithText("昨天").assertIsDisplayed()
        capture("history-date-groups")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("清空历史").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(6, vm.library.value.count { it.book.inHistory })
        compose.onNodeWithText("清空历史").performClick()
        compose.onNodeWithText("清空").performClick()
        waitForUi { compose.onNodeWithText("还没有阅读记录").isDisplayed() }
        assertEquals(.37f, runBlocking { vm.repository.dao.position("history-0")!!.progress }, 0f)
        assertEquals(6, vm.library.value.count { it.book.onShelf })
        capture("history-empty-redesign")
        compose.onNodeWithText("设置").performClick()
        waitForUi { compose.onNodeWithText("每行书籍数量").isDisplayed() }
        capture("settings-redesign")
        expandSection("小说阅读")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("阅读预览"))
        compose.onNodeWithText("护眼").performScrollTo().performClick()
        waitForUi { vm.settings.value.background == "GREEN" }
        assertEquals(3, vm.libraryColumns.value)
        capture("settings-reading-preview")
        expandSection("存储管理")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("清理漫画图片缓存"))
        compose.onNodeWithText("清理漫画图片缓存").assertIsDisplayed()
        capture("settings-storage-redesign")
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-night-xhdpi")
    fun historyAndSettingsRenderInDarkTheme() {
        seedHistory()
        compose.onNodeWithText("历史").performClick()
        waitForUi { compose.onNodeWithText("阅读记录").isDisplayed() }
        capture("history-redesign-dark")
        compose.onNodeWithText("设置").performClick()
        waitForUi { compose.onNodeWithText("每行书籍数量").isDisplayed() }
        capture("settings-redesign-dark")
        expandSection("小说阅读")
        compose.onNodeWithText("阅读预览").performScrollTo()
        capture("settings-reading-expanded-dark")
    }

    @Test fun settingsGroupsToggleIndependentlyAndKeepStateAcrossNavigation() {
        compose.onNodeWithText("设置").performClick()
        waitForUi { compose.onNodeWithText("每行书籍数量").isDisplayed() }
        assertSection("书架布局", true)
        assertSection("小说阅读", false)
        compose.onNodeWithText("阅读预览").assertDoesNotExist()
        capture("settings-folded-default")
        expandSection("小说阅读")
        compose.onNodeWithText("护眼").performScrollTo().performClick()
        waitForUi { vm.settings.value.background == "GREEN" }
        assertSection("小说阅读", true)
        assertSection("书架布局", true)
        compose.onNodeWithText("阅读预览").performScrollTo()
        capture("settings-reading-expanded")
        section("小说阅读").performScrollTo().performClick()
        waitForUi { compose.onAllNodesWithText("阅读预览").fetchSemanticsNodes().isEmpty() }
        assertEquals("GREEN", vm.settings.value.background)
        section("书架布局").performScrollTo().performClick()
        compose.onNodeWithText("3 本").assertDoesNotExist()
        capture("settings-all-collapsed")
        expandSection("导入偏好")
        compose.onNodeWithText("默认复制到应用").assertIsDisplayed()
        expandSection("小说阅读")
        compose.onNodeWithText("书库").performClick()
        compose.onNodeWithText("设置").performClick()
        assertSection("小说阅读", true)
        assertSection("导入偏好", true)
        assertSection("书架布局", false)
        compose.activityRule.scenario.recreate()
        assertSection("小说阅读", true)
        assertSection("导入偏好", true)
        assertEquals("GREEN", vm.settings.value.background)
    }

    private fun section(title: String) = compose.onNodeWithContentDescription("${title}分组")
    private fun assertSection(title: String, expanded: Boolean) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("${title}分组"))
        section(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (expanded) "已展开" else "已收起"))
    }
    private fun expandSection(title: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("${title}分组"))
        if (section(title).fetchSemanticsNode().config[SemanticsProperties.StateDescription] != "已展开") section(title).performClick()
    }

    private fun seedHistory() {
        val model = vm
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val titles = listOf("山间来信", "夜航笔记", "海岸漫画", "遥远星河", "雨后列车", "山城绘卷")
        runBlocking {
            titles.forEachIndexed { index, title ->
                val day = today.minusDays(if (index < 2) 0 else if (index < 4) 1 else 3)
                val time = day.atTime(16 - index, 20).atZone(zone).toInstant().toEpochMilli()
                model.repository.dao.putBook(Book("history-$index", title, if (index == 2 || index == 5) "COMIC" else "TEXT", "DIRECT", "file:///history/$index.txt", lastOpenedAt = time, inHistory = true))
                model.repository.dao.putPosition(ReadingPosition("history-$index", imageIndex = index * 3, progress = .37f))
            }
            model.repository.dao.putFolder(FolderSource("content://test/tree/books", "本地书籍", true))
        }
        waitForUi { model.library.value.size == 6 }
    }
    private fun waitForUi(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); condition() }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(System.getProperty("myviewer.outputDir"), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
