package com.chen.myviewer

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookshelfFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetLibrary() {
        val app = ViewModelProvider(compose.activity)[MainViewModel::class.java].getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables(); app.settings.setLibrarySort(LibrarySort.RECENT); app.settings.setLibraryColumns(3) }
    }
    @After fun cleanLibrary() {
        val app = ViewModelProvider(compose.activity)[MainViewModel::class.java].getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables(); app.settings.setLibrarySort(LibrarySort.RECENT); app.settings.setLibraryColumns(3); app.settings.update(ReaderSettings())
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

    @Test fun doubleTapAndBottomActionReturnToTopWithoutScrollbar() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        seedShelf(vm, 120)
        waitForUi { vm.library.value.size == 120 }
        chooseSort(LibrarySort.TITLE)
        compose.onNodeWithContentDescription("书库快速滚动").assertDoesNotExist()
        waitForUi { !compose.onNodeWithContentDescription("回到书库顶部").isDisplayed() }
        compose.onNodeWithContentDescription("回到书库顶部").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(119)
        waitForUi { compose.onNodeWithContentDescription("回到书库顶部").isDisplayed() }
        capture("bookshelf-bottom-action")
        compose.onNodeWithContentDescription("回到书库顶部").performClick()
        waitForUi { compose.onAllNodesWithText("书 001").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("书 001").onLast().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(119)
        waitForUi { compose.onNodeWithContentDescription("回到书库顶部").isDisplayed() }
        compose.onNodeWithText("书库").performClick()
        compose.onAllNodesWithText("书 120").onLast().assertIsDisplayed()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        compose.onNodeWithText("书库").performTouchInput { doubleClick(center) }
        waitForUi { compose.onAllNodesWithText("书 001").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("书 001").onLast().assertIsDisplayed()
        assertNull(vm.reader.value)
        capture("bookshelf-search-home")
    }

    @Test fun titleSearchFiltersClearsAndSurvivesReading() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val titles = listOf("山海第一卷", "山海第二卷", "远行", "Night Notes")
        runBlocking {
            titles.forEachIndexed { index, title ->
                val file = File(compose.activity.cacheDir, "$title.txt").apply { writeText("第1章\n这是用于书名搜索的本地小说。\n".repeat(50)) }
                vm.repository.dao.putBook(Book("search-$index", title, "TEXT", "DIRECT", Uri.fromFile(file).toString()))
            }
        }
        waitForUi { vm.library.value.size == 4 && compose.onNodeWithContentDescription("搜索书名").isDisplayed() }
        chooseSort(LibrarySort.TITLE)
        search().performTextInput("山海"); search().performImeAction()
        compose.onNodeWithText("找到 2 本书").assertExists()
        compose.onAllNodesWithText("远行").assertCountEquals(0)
        compose.onAllNodesWithText("山海第一卷").onLast().performClick()
        waitForUi { vm.reader.value?.text != null }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForUi { vm.reader.value == null }
        search().assertTextContains("山海")
        compose.onNodeWithContentDescription("排序：书名").assertExists()
        search().performTextReplacement("NIGHT"); search().performImeAction()
        compose.onNodeWithText("找到 1 本书").assertExists()
        compose.onAllNodesWithText("Night Notes").onLast().assertIsDisplayed()
        capture("bookshelf-search-result")
        search().performTextReplacement("没有这本书"); search().performImeAction()
        compose.onNodeWithText("没有找到匹配的书名").assertIsDisplayed()
        compose.onNodeWithText("清空搜索").performClick()
        search().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("4 本书").assertExists()
    }

    @Test fun columnChoiceChangesLayoutAndSurvivesRecreation() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        seedShelf(vm, 12)
        waitForUi { vm.library.value.size == 12 }
        chooseSort(LibrarySort.TITLE)
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithContentDescription("书架布局分组").performClick()
        compose.onNodeWithText("4 本").performClick()
        waitForUi { vm.libraryColumns.value == 4 }
        compose.onNodeWithText("书库").performClick()
        capture("bookshelf-four-columns")
        val four = (1..4).map { tileTop("书 ${it.toString().padStart(3, '0')}") }
        assertTrue(four.all { it == four.first() })
        assertTrue(tileTop("书 005") > four.first())
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("6 本").performClick()
        waitForUi { vm.libraryColumns.value == 6 }
        capture("settings-layout")
        compose.onNodeWithText("书库").performClick()
        compose.activityRule.scenario.recreate()
        capture("bookshelf-six-columns")
        val six = (1..6).map { tileTop("书 ${it.toString().padStart(3, '0')}") }
        assertTrue(six.all { it == six.first() })
        assertEquals(6, vm.libraryColumns.value)
    }

    @Test fun finalForwardTurnCompletesBookAndCompletionCanBeCleared() {
        compose.onNodeWithText("书库").performClick()
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val file = File(compose.activity.cacheDir, "completed-story.txt").apply { writeText("第1章\n完成标记测试。\n".repeat(100)) }
        runBlocking {
            vm.repository.dao.putBook(Book("completion-local", "完读测试", "TEXT", "DIRECT", Uri.fromFile(file).toString(), lastOpenedAt = 1))
        }
        waitForUi { compose.onAllNodesWithText("完读测试").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("完读测试").onLast().performClick()
        waitForUi { vm.reader.value?.text != null }
        compose.runOnIdle { vm.seekText(vm.reader.value!!.text!!.text.lastIndex) }
        compose.waitForIdle()
        assertFalse(vm.library.value.single().book.completed)
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .9f, height * .5f)) }
        waitForUi { vm.reader.value == null && vm.library.value.single().book.completed }
        compose.onNode(hasText("已读完") and hasAnyAncestor(hasScrollToIndexAction())).assertExists()
        compose.onNodeWithText("继续阅读").assertDoesNotExist()
        capture("bookshelf-completed")
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("已读完") and hasAnyAncestor(hasScrollToIndexAction())).assertExists()
        compose.onAllNodesWithText("完读测试").onLast().performTouchInput { longClick(center) }
        compose.onNodeWithText("取消已读完标记").performClick()
        val current = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        waitForUi { !current.library.value.single().book.completed }
        compose.onNode(hasText("已读完") and hasAnyAncestor(hasScrollToIndexAction())).assertDoesNotExist()
        compose.onNodeWithText("继续阅读").assertExists()
    }

    @Test fun completionFilterCombinesWithCategorySearchAndSurvivesReadingAndRecreation() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val file = File(compose.activity.cacheDir, "filter-story.txt").apply { writeText("第1章\n筛选测试文字。\n".repeat(50)) }
        runBlocking {
            val books = listOf(
                Book("done-text", "山海完读", "TEXT", "DIRECT", Uri.fromFile(file).toString(), completed = true),
                Book("unread-text", "山海未完", "TEXT", "DIRECT", "file:///unread.txt"),
                Book("end-text", "另一部未完", "TEXT", "DIRECT", "file:///end.txt"),
                Book("done-comic", "山海漫画完成", "COMIC", "ONLINE", "myviewer://done", completed = true),
                Book("unread-comic", "山海漫画未完", "COMIC", "DIRECT", "file:///unread.zip"),
            )
            books.forEach { vm.repository.dao.putBook(it) }
            vm.repository.dao.putPosition(com.chen.myviewer.data.ReadingPosition("end-text", progress = 1f))
        }
        waitForUi { vm.library.value.size == 5 }
        compose.onNodeWithContentDescription("阅读状态：全部状态").assertIsSelected()
        compose.onNodeWithContentDescription("阅读状态：已读完").performClick()
        compose.onNodeWithText("2 本书").assertExists()
        compose.onAllNodesWithText("山海未完").assertCountEquals(0)
        search().performTextInput("山海"); search().performImeAction()
        compose.onNodeWithText("小说").performClick()
        compose.onNodeWithText("找到 1 本书").assertExists()
        compose.onAllNodesWithText("山海完读").onLast().performClick()
        waitForUi { vm.reader.value?.text != null }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForUi { vm.reader.value == null }
        compose.onNodeWithContentDescription("阅读状态：已读完").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("阅读状态：已读完").assertIsSelected()
        compose.onNodeWithText("小说").assertIsSelected()
        search().assertTextContains("山海")
        capture("bookshelf-completion-filter")
        compose.onNodeWithContentDescription("清空书名搜索").performClick()
        compose.onNodeWithText("全部").performClick()
        compose.onNodeWithContentDescription("阅读状态：未读完").performClick()
        compose.onNodeWithText("3 本书").assertExists()
        compose.onAllNodesWithText("另一部未完").onLast().assertIsDisplayed()
        compose.onNodeWithText("漫画").performClick()
        compose.onNodeWithText("1 本书").assertExists()
        val current = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        runBlocking { current.repository.dao.setCompleted("unread-comic", true) }
        waitForUi { compose.onNodeWithText("没有符合筛选条件的书籍").isDisplayed() }
        compose.onNodeWithText("显示全部状态").performClick()
        compose.onNodeWithContentDescription("阅读状态：全部状态").assertIsSelected()
        compose.onNodeWithText("2 本书").assertExists()
    }

    private fun seedShelf(vm: MainViewModel, count: Int) = runBlocking {
        for (index in 1..count) vm.repository.dao.putBook(Book("shelf-$index", "书 ${index.toString().padStart(3, '0')}", "TEXT", "DIRECT", "file:///shelf/$index.txt", importedAt = index.toLong()))
    }
    private fun search() = compose.onNodeWithContentDescription("搜索书名")
    private fun tileTop(title: String) = compose.onAllNodesWithText(title).onLast().fetchSemanticsNode().boundsInRoot.top

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
