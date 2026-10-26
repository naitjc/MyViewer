package com.chen.myviewer

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.Book
import com.chen.myviewer.data.LibrarySort
import com.chen.myviewer.data.ReaderSettings
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookshelfFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetLibrary() {
        val app = ViewModelProvider(compose.activity)[MainViewModel::class.java].getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables(); app.settings.setLibrarySort(LibrarySort.RECENT) }
    }
    @After fun cleanLibrary() {
        val app = ViewModelProvider(compose.activity)[MainViewModel::class.java].getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables(); app.settings.setLibrarySort(LibrarySort.RECENT); app.settings.update(ReaderSettings())
        }
    }

    @Test fun sortingAndCategorySurviveReadingAndActivityRecreation() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val titles = listOf("甲", "乙", "丙")
        runBlocking {
            for ((index, title) in titles.withIndex()) {
                val source = File(compose.activity.cacheDir, "$title.txt").apply { writeText("第1章\n这是用于验证书库排序的小说。\n".repeat(100)) }
                vm.repository.dao.putBook(Book("sort-$index", title, "TEXT", "DIRECT", Uri.fromFile(source).toString(), importedAt = 1234))
            }
        }
        waitForUi { vm.library.value.size == 3 && titles.all { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } }
        compose.onNodeWithText("小说").performClick()
        for (sort in listOf(LibrarySort.TITLE, LibrarySort.IMPORTED)) {
            chooseSort(sort)
            capture("bookshelf-sort-${sort.name.lowercase()}")
            val before = tileOrder(titles)
            assertEquals(titles.sorted(), before)
            compose.onAllNodesWithText("乙").onLast().performClick()
            waitForUi { vm.reader.value?.text != null }
            compose.runOnIdle { vm.updateSettings(vm.settings.value.copy(fontSize = 26f)) }
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            waitForUi { vm.reader.value == null }
            compose.onNodeWithContentDescription("排序：${sort.label}").assertExists()
            compose.onNodeWithText("小说").assertIsSelected()
            assertEquals("Reading must not reorder tied titles or import timestamps", before, tileOrder(titles))
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("排序：导入时间").assertExists()
        compose.onNodeWithText("小说").assertIsSelected()
    }

    @Test fun fastScrollbarDragsToBothEndsFollowsSwipesAndHidesForEmptyCategories() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        fun seed(range: IntRange) = runBlocking {
            for (index in range) vm.repository.dao.putBook(Book("shelf-$index", "书 ${index.toString().padStart(3, '0')}", "TEXT", "DIRECT", "file:///shelf/$index.txt", importedAt = index.toLong()))
        }
        seed(1..3)
        waitForUi { compose.onAllNodesWithText("书 001").fetchSemanticsNodes().isNotEmpty() }
        scrollbar().assertDoesNotExist()
        seed(4..120)
        waitForUi { compose.onAllNodesWithContentDescription("书库快速滚动").fetchSemanticsNodes().isNotEmpty() }
        chooseSort(LibrarySort.TITLE)
        compose.onRoot().performTouchInput { swipe(Offset(width * .4f, height * .7f), Offset(width * .4f, height * .25f), 300) }
        waitForUi { progress() > .01f }
        scrollbar().performTouchInput { click(Offset(centerX, centerY)) }
        waitForUi { progress() in .35f.. .65f }
        scrollbar().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, height - 1f), 400) }
        waitForUi { progress() >= .99f }
        compose.onAllNodesWithText("书 120").onLast().assertIsDisplayed()
        assertNull("Dragging the bar must not open a book", vm.reader.value)
        scrollbar().performTouchInput { swipe(Offset(centerX, height - 1f), Offset(centerX, 1f), 400) }
        waitForUi { progress() <= .01f }
        compose.onAllNodesWithText("书 001").onLast().assertIsDisplayed()
        compose.onNodeWithText("漫画").performClick()
        compose.onNodeWithText("这个分类还没有书籍。").assertIsDisplayed()
        scrollbar().assertDoesNotExist()
        compose.onNodeWithText("小说").performClick()
        waitForUi { compose.onAllNodesWithContentDescription("书库快速滚动").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0f, progress(), .01f)
        capture("bookshelf-fast-scroll")
    }

    private fun chooseSort(sort: LibrarySort) {
        compose.onNodeWithContentDescription("排序：", substring = true).performClick()
        compose.onNodeWithText(sort.label).performClick()
        waitForUi { compose.onAllNodesWithContentDescription("排序：${sort.label}").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tileOrder(titles: List<String>): List<String> {
        waitForUi { titles.all { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } }
        val bounds = titles.associateWith { compose.onAllNodesWithText(it).onLast().fetchSemanticsNode().boundsInRoot }
        return titles.sortedWith(compareBy<String> { bounds.getValue(it).top }.thenBy { bounds.getValue(it).left })
    }
    private fun scrollbar() = compose.onNodeWithContentDescription("书库快速滚动")
    private fun progress() = scrollbar().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
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
