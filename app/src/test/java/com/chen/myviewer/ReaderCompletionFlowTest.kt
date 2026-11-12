package com.chen.myviewer

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
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
class ReaderCompletionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    @After fun clean() {
        val model = vm
        val application = model.getApplication<ViewerApplication>()
        model.closeReader()
        runBlocking(Dispatchers.IO) { application.database.clearAllTables() }
    }
    @Test fun comicPageAndContinuousReadersFinishOnlyAfterAnotherForwardGesture() {
        for (mode in listOf("PAGE", "SCROLL")) {
            val file = File(compose.activity.cacheDir, "finish-$mode.zip")
            TestComicZip.write(file, (1..3).associate { "$it.png" to (300 to 1100) })
            val book = open(file, mode)
            compose.runOnIdle { vm.seekComic(2) }
            waitForUi { vm.reader.value?.comic?.cachedPage(2) != null }
            assertFalse(runBlocking { vm.repository.dao.book(book.id) }!!.completed)
            if (mode == "PAGE") {
                compose.onRoot().performTouchInput { swipeLeft() }
            } else {
                compose.onRoot().performTouchInput { swipeUp() }
                assertNotNull("Arriving at the end must keep the reader open", vm.reader.value)
                compose.runOnIdle { vm.seekComic(2, 1f) }
                compose.waitForIdle()
                compose.onRoot().performTouchInput { swipeUp() }
            }
            waitForUi { vm.reader.value == null && runBlocking { vm.repository.dao.book(book.id) }!!.completed }
            assertEquals(1f, runBlocking { vm.repository.dao.position(book.id) }!!.progress)
        }
    }
    @Test fun continuousTextFinishesOnANewUpwardGestureAtTheEnd() {
        val file = File(compose.activity.cacheDir, "finish-scroll.txt").apply { writeText("第1章\n" + "原创连续阅读文字。\n".repeat(300)) }
        val book = open(file, "SCROLL")
        compose.runOnIdle { vm.seekText(vm.reader.value!!.text!!.text.lastIndex) }
        compose.waitForIdle()
        assertFalse(runBlocking { vm.repository.dao.book(book.id) }!!.completed)
        compose.onRoot().performTouchInput { swipeDown() }
        assertNotNull(vm.reader.value)
        compose.runOnIdle { vm.seekText(vm.reader.value!!.text!!.text.lastIndex) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeUp() }
        waitForUi { vm.reader.value == null && runBlocking { vm.repository.dao.book(book.id) }!!.completed }
    }
    private fun open(file: File, mode: String): Book = runBlocking {
        val result = vm.repository.import(listOf(ImportCandidate(Uri.fromFile(file), file.name, file.length())), false) { _, _ -> }
        assertTrue(result.failures.toString(), result.failures.isEmpty())
        val book = vm.repository.dao.byUri(Uri.fromFile(file).toString())!!
        vm.repository.dao.putPreferences(BookPreferences(book.id, mode))
        compose.runOnIdle { vm.open(book) }
        waitForUi { vm.reader.value != null }
        book
    }
    private fun waitForUi(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30))
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("Timed out waiting for completion", condition())
    }
}
