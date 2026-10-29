package com.chen.myviewer

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoReadingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Before fun reset() {
        val app = vm.getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.settings.update(ReaderSettings(autoPageSeconds = 2, autoScrollSpeed = 10))
        }
        waitForUi { vm.settings.value.autoPageSeconds == 2 }
    }

    @After fun clean() {
        compose.mainClock.autoAdvance = false
        val model = vm
        compose.runOnIdle { model.closeReader() }
        compose.mainClock.advanceTimeBy(32)
        runBlocking(Dispatchers.IO) {
            model.getApplication<ViewerApplication>().database.clearAllTables()
            model.getApplication<ViewerApplication>().settings.update(ReaderSettings())
        }
    }

    @Test fun comicPageTimerCanPauseOnTouchInBackgroundAndAtTheEnd() {
        openComic("PAGE")
        openControls()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        waitForUi { vm.settings.value.autoPageSeconds == 3 }
        compose.onNodeWithText("翻页间隔 · 3 秒／页").assertExists()
        startFromControls()
        advance(3300)
        waitForUi { vm.reader.value!!.position.imageIndex > 0 }
        val stoppedAt = vm.reader.value!!.position.imageIndex
        compose.onRoot().performTouchInput { click(center) }
        advance(3500)
        compose.onNodeWithText("暂停自动阅读").assertDoesNotExist()
        assertEquals(stoppedAt, vm.reader.value!!.position.imageIndex)

        openControls(); startFromControls()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(64)
        val backgroundAt = vm.reader.value!!.position.imageIndex
        advance(3500)
        compose.onNodeWithText("暂停自动阅读").assertDoesNotExist()
        assertEquals(backgroundAt, vm.reader.value!!.position.imageIndex)

        openControls(); startFromControls()
        compose.runOnIdle { vm.seekComic(vm.reader.value!!.comic!!.images.lastIndex) }
        waitForUi { vm.reader.value!!.comic!!.cachedPage(9) != null }
        advance(3300)
        waitForUi { compose.onAllNodesWithText("暂停自动阅读").fetchSemanticsNodes().isEmpty() }
        assertEquals(9, vm.reader.value!!.position.imageIndex)
        val book = vm.reader.value!!.book
        compose.runOnIdle { vm.closeReader() }
        advance(64)
        compose.runOnIdle { vm.open(book) }
        waitForUi { vm.reader.value?.comic != null }
        compose.onNodeWithText("暂停自动阅读").assertDoesNotExist()
        assertEquals(3, vm.settings.value.autoPageSeconds)
    }

    @Test fun continuousComicScrollsPausesAndStopsAtTheBottomWithSavedProgress() {
        openComic("SCROLL")
        openControls()
        compose.onNodeWithText("滚动速度 · 10 档").assertExists()
        startFromControls()
        advance(1800)
        compose.onNodeWithText("暂停自动阅读").performClick()
        advance(64)
        val stopped = vm.reader.value!!.position
        assertTrue(stopped.imageIndex > 0 || stopped.imageRatio > 0f)
        advance(1800)
        assertEquals(stopped.imageIndex, vm.reader.value!!.position.imageIndex)
        assertEquals(stopped.imageRatio, vm.reader.value!!.position.imageRatio, .001f)

        openControls(); startFromControls()
        compose.runOnIdle { vm.seekComic(9, 1f) }
        waitForUi { vm.reader.value!!.comic!!.cachedPage(9) != null }
        advance(500)
        waitForUi { compose.onAllNodesWithText("暂停自动阅读").fetchSemanticsNodes().isEmpty() }
        val last = vm.reader.value!!.position
        assertTrue(last.progress > .8f)
        compose.runOnIdle { vm.closeReader() }
        advance(64)
        waitForUi { runBlocking { vm.repository.dao.position(last.bookId)?.progress ?: 0f } > .8f }
    }

    @Test fun novelSupportsAutomaticPagesAndContinuousScrollingWithManualPause() {
        val file = File(compose.activity.cacheDir, "自动小说.txt").apply {
            writeText("第一章 自动阅读\n" + "这是一段用于验证小说自动翻页和自动滚动的正文。\n".repeat(300))
        }
        open(file, "PAGE")
        openControls(); startFromControls()
        advance(2300)
        waitForUi { vm.reader.value!!.position.textOffset > 0 }
        compose.onNodeWithText("暂停自动阅读").performClick()
        advance(64)
        val paged = vm.reader.value!!.position.textOffset
        advance(2600)
        assertEquals(paged, vm.reader.value!!.position.textOffset)

        showMenu()
        compose.onNodeWithText("切换连续").performClick()
        waitForUi { vm.reader.value!!.preferences.mode == "SCROLL" }
        openControls(); startFromControls()
        advance(1800)
        compose.onNodeWithText("暂停自动阅读").performClick()
        advance(64)
        assertTrue(vm.reader.value!!.position.textOffset > paged)
        val scrolled = vm.reader.value!!.position.textOffset
        advance(2600)
        assertEquals(scrolled, vm.reader.value!!.position.textOffset)
    }

    private fun openComic(mode: String) {
        val file = File(compose.activity.cacheDir, "自动漫画-$mode.zip")
        TestComicZip.write(file, (1..10).associate { "${it.toString().padStart(3, '0')}.png" to (160 to 320) })
        open(file, mode)
        waitForUi { vm.reader.value!!.comic!!.cachedPage(0) != null }
    }

    private fun open(file: File, mode: String) {
        val model = vm
        val book = runBlocking {
            val result = model.repository.import(listOf(ImportCandidate(Uri.fromFile(file), file.name, file.length())), false) { _, _ -> }
            assertEquals(result.failures.toString(), 1, result.added)
            model.repository.dao.byUri(Uri.fromFile(file).toString())!!.also { model.repository.dao.putPreferences(BookPreferences(it.id, mode)) }
        }
        compose.runOnIdle { model.open(book) }
        waitForUi { model.reader.value != null || model.error.value != null }
        assertNull(model.error.value)
    }

    private fun showMenu() {
        if (!compose.onNodeWithContentDescription("自动阅读").isDisplayed()) {
            compose.onRoot().performTouchInput { click(center) }
            advance(500)
        }
        waitForUi { compose.onNodeWithContentDescription("自动阅读").isDisplayed() }
    }

    private fun openControls() {
        showMenu()
        compose.onNodeWithContentDescription("自动阅读").performClick()
        advance(500)
        waitForUi { compose.onNodeWithText("开始自动阅读").isDisplayed() }
    }

    private fun startFromControls() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("开始自动阅读").performClick()
        advance(64)
        compose.onNodeWithText("暂停自动阅读").assertIsDisplayed()
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        compose.mainClock.advanceTimeBy(32)
    }

    private fun waitForUi(condition: () -> Boolean) = compose.waitUntil(15000) {
        if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}
