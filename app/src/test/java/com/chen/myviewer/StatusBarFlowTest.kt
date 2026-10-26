package com.chen.myviewer

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.ImportCandidate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], qualifiers = "w393dp-h851dp-notnight-xhdpi")
class StatusBarFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun comicUsesWhiteIconsAndReturnsToLightShelfIcons() = openComicAndReturn(shelfUsesDarkIcons = true)

    @Test
    @Config(qualifiers = "w393dp-h851dp-night-xhdpi")
    fun darkShelfAndComicBothKeepWhiteIcons() = openComicAndReturn(shelfUsesDarkIcons = false)

    private fun openComicAndReturn(shelfUsesDarkIcons: Boolean) {
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        waitForUi { controller.isAppearanceLightStatusBars == shelfUsesDarkIcons }
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val source = File(compose.activity.cacheDir, "状态栏测试.zip").apply {
            writeBytes(StatusBarFlowTest::class.java.getResourceAsStream("/sample-comic.zip")!!.use { it.readBytes() })
        }
        runBlocking {
            val result = vm.repository.import(listOf(ImportCandidate(Uri.fromFile(source), source.name, source.length())), false) { _, _ -> }
            assertEquals(result.failures.toString(), 1, result.added)
        }
        waitForUi { compose.onAllNodesWithText("状态栏测试").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("状态栏测试").onFirst().performClick()
        waitForUi { vm.reader.value?.comic != null }
        waitForUi { !controller.isAppearanceLightStatusBars }
        for (mode in listOf("SCROLL", "PAGE")) {
            compose.runOnIdle { vm.mode(mode) }
            waitForUi { vm.reader.value?.preferences?.mode == mode }
            assertFalse("Comic status icons must stay white in $mode", controller.isAppearanceLightStatusBars)
        }
        compose.runOnIdle { vm.closeReader() }
        waitForUi { vm.reader.value == null && controller.isAppearanceLightStatusBars == shelfUsesDarkIcons }
    }

    private fun waitForUi(condition: () -> Boolean) = compose.waitUntil(10000) { shadowOf(Looper.getMainLooper()).idle(); condition() }
}
