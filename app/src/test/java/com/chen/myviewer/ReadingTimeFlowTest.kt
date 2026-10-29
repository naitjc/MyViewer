package com.chen.myviewer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.*
import com.chen.myviewer.ui.formatReadingTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingTimeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]
    private val today get() = LocalDate.now().toString()
    private val power get() = compose.activity.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguard get() = compose.activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    @Before fun setup() {
        val app = vm.getApplication<ViewerApplication>()
        shadowOf(power).setIsInteractive(true); shadowOf(keyguard).setKeyguardLocked(false)
        runBlocking(Dispatchers.IO) { app.database.clearAllTables(); app.settings.update(ReaderSettings(autoPageSeconds = 2)) }
    }
    @After fun cleanup() {
        compose.mainClock.autoAdvance = false
        val model = vm
        compose.runOnIdle { model.closeReader() }
        compose.mainClock.advanceTimeBy(32)
        shadowOf(power).setIsInteractive(true); shadowOf(keyguard).setKeyguardLocked(false)
        runBlocking(Dispatchers.IO) { model.getApplication<ViewerApplication>().settings.update(ReaderSettings()) }
    }

    @Test fun foregroundClockPausesForBackgroundScreenOffAndLock() {
        openNovel()
        val before = total()
        val start = SystemClock.elapsedRealtime()
        advance(8000)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val paused = awaitTotal(before + SystemClock.elapsedRealtime() - start)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(paused, total())

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        val resumed = SystemClock.elapsedRealtime()
        advance(8000)
        shadowOf(power).setIsInteractive(false)
        compose.activity.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        val locked = awaitTotal(paused + SystemClock.elapsedRealtime() - resumed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(locked, total())

        shadowOf(power).setIsInteractive(true); shadowOf(keyguard).setKeyguardLocked(true)
        compose.activity.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        advance(6000)
        assertEquals(locked, total())
        shadowOf(keyguard).setKeyguardLocked(false)
        compose.activity.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idle()
        advance(6000)
        compose.runOnIdle { vm.closeReader() }
        assertTrue(awaitTotal(locked + 5000) > locked)
    }

    @Test fun bookSwitchAutomaticReadingAndClearHistoryKeepAccurateTotals() {
        val first = openNovel()
        advance(8000)
        val secondFile = File(compose.activity.cacheDir, "计时漫画.zip")
        TestComicZip.write(secondFile, (1..10).associate { "$it.png" to (160 to 320) })
        val second = imported(secondFile)
        compose.runOnIdle { vm.open(second) }
        waitForUi { vm.reader.value?.book?.id == second.id && vm.reader.value?.comic?.cachedPage(0) != null }
        val firstDuration = awaitBookTime(first.id, 7000)
        compose.onRoot().performTouchInput { click(center) }; advance(500)
        compose.onNodeWithContentDescription("自动阅读").performClick()
        waitForUi { compose.onNodeWithText("开始自动阅读").isDisplayed() }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("开始自动阅读").performClick(); advance(7500)
        compose.runOnIdle { vm.closeReader() }
        compose.mainClock.advanceTimeBy(64)
        val secondDuration = awaitBookTime(second.id, 6000)
        assertEquals(firstDuration, bookTime(first.id))
        assertEquals(firstDuration + secondDuration, total())

        compose.onNodeWithText("历史").performClick()
        waitForUi { compose.onNodeWithText("阅读时长").isDisplayed() }
        compose.onNodeWithText("今日阅读").assertIsDisplayed()
        waitForUi { vm.bookReadingTimes.value[second.id] == secondDuration }
        capture("reading-time-history")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("累计阅读 ${formatReadingTime(secondDuration)}"))
        compose.onNodeWithText("累计阅读 ${formatReadingTime(secondDuration)}").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        capture("reading-time-history")
        compose.onNodeWithText("清空历史").performScrollTo().performClick()
        waitForUi { compose.onNodeWithText("清空").isDisplayed() }
        compose.onNodeWithText("清空").performClick()
        waitForUi { compose.onNodeWithText("还没有阅读记录").isDisplayed() }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("累计阅读").assertIsDisplayed()
        assertEquals(firstDuration + secondDuration, total())
        assertEquals(firstDuration, bookTime(first.id))
        capture("reading-time-cleared-history")
    }

    private fun openNovel(): Book {
        val file = File(compose.activity.cacheDir, "计时小说.txt").apply { writeText("第一章 阅读时间\n" + "这是一段用于验证阅读时间的正文。\n".repeat(150)) }
        val book = imported(file)
        compose.runOnIdle { vm.open(book) }
        waitForUi { vm.reader.value?.text != null }
        compose.waitForIdle()
        return book
    }
    private fun imported(file: File): Book {
        val model = vm
        return runBlocking {
            val result = model.repository.import(listOf(ImportCandidate(Uri.fromFile(file), file.name, file.length())), false) { _, _ -> }
            assertEquals(result.failures.toString(), 1, result.added)
            model.repository.dao.byUri(Uri.fromFile(file).toString())!!
        }
    }
    private fun total() = runBlocking { vm.readingTotals(today).first().totalMs }
    private fun bookTime(id: String) = runBlocking { vm.repository.dao.observeBookReadingTimes().first().firstOrNull { it.bookId == id }?.totalMs ?: 0 }
    private fun awaitTotal(minimum: Long): Long = runBlocking {
        withTimeout(5000) { vm.readingTotals(today).first { it.totalMs >= minimum }.totalMs }
    }
    private fun awaitBookTime(id: String, minimum: Long): Long = runBlocking {
        withTimeout(5000) { vm.repository.dao.observeBookReadingTimes().first { rows -> (rows.firstOrNull { it.bookId == id }?.totalMs ?: 0) >= minimum }.first { it.bookId == id }.totalMs }
    }
    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        compose.mainClock.advanceTimeBy(32)
    }
    private fun waitForUi(condition: () -> Boolean) = compose.waitUntil(15000) {
        if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle(); condition()
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        shadowOf(Looper.getMainLooper()).idle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(System.getProperty("myviewer.outputDir"), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
