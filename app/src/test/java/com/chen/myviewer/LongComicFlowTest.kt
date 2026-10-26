package com.chen.myviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
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
class LongComicFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun switchingFromContinuousToPageModeKeepsBothTapEdgesWorking() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val source = File(compose.activity.cacheDir, "切换模式.zip").apply { writeBytes(LongComicFlowTest::class.java.getResourceAsStream("/long-comic-48-pages.zip")!!.use { it.readBytes() }) }
        runBlocking { vm.repository.import(listOf(ImportCandidate(Uri.fromFile(source), source.name, source.length())), false) { _, _ -> } }
        waitForUi { compose.onAllNodesWithText("切换模式").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("切换模式").onFirst().performClick()
        waitForUi { vm.reader.value?.comic?.cachedPage(0) != null }
        waitForUi { hasNoBlankBands(frame()) }
        compose.onRoot().performTouchInput { click(center) }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        waitForUi { compose.onAllNodesWithText("切换翻页").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("切换翻页").performClick()
        waitForUi { vm.reader.value!!.preferences.mode == "PAGE" }
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .9f, centerY)) }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        waitForUi { vm.reader.value!!.position.imageIndex == 1 }
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .1f, centerY)) }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        waitForUi { vm.reader.value!!.position.imageIndex == 0 }
        compose.runOnIdle { vm.closeReader() }
    }
    @Test fun preloadsLongComicAndSavesLatestPositionWithoutPerPixelPublication() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val source = File(compose.activity.cacheDir, "48页长条.zip").apply { writeBytes(LongComicFlowTest::class.java.getResourceAsStream("/long-comic-48-pages.zip")!!.use { it.readBytes() }) }
        runBlocking {
            val result = vm.repository.import(listOf(ImportCandidate(Uri.fromFile(source), source.name, source.length())), false) { _, _ -> }
            assertEquals(result.failures.toString(), 1, result.added)
        }
        waitForUi { compose.onAllNodesWithText("48页长条").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("48页长条").onFirst().performClick()
        waitForUi { vm.reader.value?.comic != null || vm.error.value != null }
        assertNull(vm.error.value)
        val archive = vm.reader.value!!.comic!!
        assertEquals(48, archive.images.size)
        assertEquals("SCROLL", vm.reader.value!!.preferences.mode)
        waitForUi { archive.cachedPage(0) != null && archive.cachedPage(3) != null }
        assertNull("Opening must not decode the entire book", archive.cachedPage(40))
        waitForUi { hasNoBlankBands(frame()) }
        repeat(3) {
            compose.onRoot().performTouchInput { swipeUp(durationMillis = 90) }
            waitForUi { (vm.reader.value?.position?.imageRatio ?: 0f) > 0f || (vm.reader.value?.position?.imageIndex ?: 0) > 0 }
            waitForUi { hasNoBlankBands(frame()) }
        }
        capture("long-comic-after-fling")
        compose.runOnIdle { vm.seekComic(32) }
        waitForUi { archive.cachedPage(35) != null }
        compose.runOnIdle { vm.seekComic(20) }
        waitForUi { archive.cachedPage(17) != null }
        val id = vm.reader.value!!.book.id
        compose.runOnIdle {
            vm.comicPosition(1, 0f, true)
            val published = vm.reader.value
            repeat(50) { vm.comicPosition(1, it / 100f, false) }
            assertSame("Every pixel must not recompose the reader", published, vm.reader.value)
            vm.closeReader()
        }
        waitForUi { runBlocking { vm.repository.dao.position(id)?.imageRatio == .49f } }
        assertEquals(1, runBlocking { vm.repository.dao.position(id)!!.imageIndex })
    }
    private fun hasNoBlankBands(bitmap: Bitmap): Boolean = listOf(30, bitmap.width / 2, bitmap.width - 30).all { x ->
        (120 until bitmap.height - 100 step 100).all { y -> bitmap.getPixel(x, y).let { Color.alpha(it) == 255 && it != Color.BLACK } }
    }
    private fun waitForUi(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(android.os.Looper.getMainLooper()).idle(); condition() }
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
