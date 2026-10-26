package com.chen.myviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.ImportCandidate
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
class ComicFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun comicRendersZoomsTurnsAndRemembersMode() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val source = File(compose.activity.cacheDir, "测试漫画.zip").apply { writeBytes(ComicFlowTest::class.java.getResourceAsStream("/sample-comic.zip")!!.use { it.readBytes() }) }
        runBlocking { val result = vm.repository.import(listOf(ImportCandidate(Uri.fromFile(source), source.name, source.length())), false) { _, _ -> }; assertEquals(result.failures.toString(), 1, result.added) }
        waitForUi { compose.onAllNodesWithText("测试漫画").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("测试漫画").onFirst().performClick()
        waitForUi { vm.reader.value?.comic != null || vm.error.value != null }
        assertNull(vm.error.value)
        assertEquals("RTL", vm.reader.value!!.preferences.direction)
        assertEquals("PAGE", vm.reader.value!!.preferences.mode)
        waitForUi { val bitmap = frame(); bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) != Color.BLACK }
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .15f, centerY)) }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        waitForUi { vm.reader.value!!.position.imageIndex == 1 }
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .85f, centerY)) }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        waitForUi { vm.reader.value!!.position.imageIndex == 0 }
        capture("comic-page")
        compose.onRoot().performTouchInput { doubleClick(center) }
        waitForUi { vm.reader.value!!.position.imageRatio > 0f }
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.onRoot().performTouchInput { swipeRight() }
        waitForUi { vm.reader.value!!.position.imageIndex > 0 }
        compose.onRoot().performTouchInput { click(center) }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        compose.onNodeWithText("切换连续").performClick()
        waitForUi { vm.reader.value!!.preferences.mode == "SCROLL" }
        frame(); capture("comic-continuous")
        zoom(100).assertExists()
        compose.onRoot().performTouchInput {
            down(0, androidx.compose.ui.geometry.Offset(width * .4f, centerY))
            down(1, androidx.compose.ui.geometry.Offset(width * .6f, centerY))
            for (fraction in listOf(.3f, .2f, .1f)) {
                moveTo(0, androidx.compose.ui.geometry.Offset(width * fraction, centerY))
                moveTo(1, androidx.compose.ui.geometry.Offset(width * (1f - fraction), centerY))
            }
            up(1); up(0)
        }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        zoom(100).assertDoesNotExist()
        capture("comic-continuous-inline-zoom")
        compose.onRoot().performTouchInput { doubleClick(center) }
        zoom(100).assertExists()
        compose.onRoot().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        zoom(250).assertExists()
        compose.onRoot().performTouchInput { up() }
        zoom(100).assertExists()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        val book = vm.reader.value!!.book
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForUi { vm.reader.value == null }
        compose.onAllNodesWithText("测试漫画").onFirst().performClick()
        waitForUi { vm.reader.value?.book?.id == book.id }
        assertEquals("SCROLL", vm.reader.value!!.preferences.mode)
        assertEquals("RTL", vm.reader.value!!.preferences.direction)
        compose.runOnIdle { vm.closeReader() }
    }
    private fun zoom(percent: Int) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 $percent%"))
    private fun waitForUi(condition: () -> Boolean) = compose.waitUntil(10000) { shadowOf(android.os.Looper.getMainLooper()).idle(); condition() }
    private fun frame(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle { val view = compose.activity.window.decorView; bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap)) }
        return bitmap
    }
    private fun capture(name: String) {
        val file = File(System.getProperty("myviewer.outputDir"), "$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { frame().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
