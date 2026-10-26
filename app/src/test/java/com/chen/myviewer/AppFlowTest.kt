package com.chen.myviewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.ImportCandidate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun launchImportReadBookmarkAndResume() {
        compose.onNodeWithText("导入第一本书").assertIsDisplayed()
        capture("empty-shelf")
        compose.onNodeWithText("导入第一本书").performClick()
        compose.onNodeWithText("选择文件，可多选").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val source = File(compose.activity.cacheDir, "测试小说.txt").apply {
            writeText((1..20).joinToString("\n\n") { chapter -> "第${chapter}章 雨后的车站\n" + (1..25).joinToString("\n") { "雨停时，站台只剩下最后一盏灯。林舟把信折好，沿着石阶慢慢走下去。" } })
        }
        runBlocking { vm.repository.import(listOf(ImportCandidate(Uri.fromFile(source), source.name, source.length())), true) { _, _ -> } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("测试小说").fetchSemanticsNodes().isNotEmpty() }
        capture("bookshelf")
        compose.onAllNodesWithText("测试小说").onFirst().performClick()
        waitForUi { vm.reader.value?.text != null || vm.error.value != null }
        assertNull("Reader open error: ${vm.error.value}", vm.error.value)
        compose.waitForIdle()
        compose.onRoot().performTouchInput { click(center) }
        compose.onNodeWithText("切换连续").assertIsDisplayed().performClick()
        waitForUi { vm.reader.value?.preferences?.mode == "SCROLL" }
        compose.onRoot().performTouchInput { click(center) }
        compose.onRoot().performTouchInput { swipeUp() }
        waitForUi { (vm.reader.value?.position?.textOffset ?: 0) > 0 }
        capture("novel-reader")
        compose.onRoot().performTouchInput { click(center) }
        compose.onNodeWithContentDescription("手动书签").performClick()
        compose.onNodeWithText("＋ 保存当前位置").performClick()
        waitForUi { runBlocking { vm.repository.dao.bookmarks(vm.reader.value!!.book.id).first().isNotEmpty() } }
        capture("bookmarks")
        val id = vm.reader.value!!.book.id
        val offset = vm.reader.value!!.position.textOffset
        assertTrue(offset > 0)
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
            dialog.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("返回书架").performClick()
        waitForUi { vm.reader.value == null }
        compose.onAllNodesWithText("测试小说").onFirst().performClick()
        waitForUi { vm.reader.value?.book?.id == id }
        assertEquals(offset, vm.reader.value!!.position.textOffset)
        compose.runOnIdle { vm.closeReader() }
    }
    private fun waitForUi(condition: () -> Boolean) {
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            condition()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(System.getProperty("myviewer.outputDir"), "$name.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val view = if (dialog?.isShowing == true) dialog.window!!.decorView else compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
