package com.chen.myviewer

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.chen.myviewer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
class ComicThumbnailFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    @Before fun reset() {
        val app = vm.getApplication<ViewerApplication>()
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
    }
    @After fun clean() {
        val model = vm
        compose.runOnIdle { model.closeReader() }
        runBlocking(Dispatchers.IO) { model.getApplication<ViewerApplication>().database.clearAllTables() }
    }

    @Test fun directAndCopiedComicsShowTwentyPagesAndRestoreOldThumbnailPosition() {
        val model = vm
        val entries = linkedMapOf<String, Pair<Int, Int>>()
        repeat(20) { index ->
            val name = (index + 1).toString().padStart(3, '0')
            entries["$name.png"] = 160 to 320; entries["${name}_thumb.png"] = 40 to 80
        }
        for (copy in listOf(false, true)) {
            val source = File(compose.activity.cacheDir, "20页含缩略图-$copy.zip")
            try {
                TestComicZip.write(source, entries)
                val book = runBlocking {
                    val result = model.repository.import(listOf(ImportCandidate(Uri.fromFile(source), source.name, source.length())), copy) { _, _ -> }
                    assertEquals(result.failures.toString(), 1, result.added)
                    val imported = model.repository.dao.observeLibrary().first().single { it.book.sourceUri == Uri.fromFile(source).toString() }.book
                    model.repository.dao.putPosition(ReadingPosition(imported.id, imageIndex = 19, imageEntry = "010_thumb.png", imageRatio = .25f, progress = .5f))
                    model.repository.dao.putPreferences(BookPreferences(imported.id, "PAGE"))
                    imported
                }
                if (copy) source.delete()
                compose.runOnIdle { model.open(book) }
                waitForUi { model.reader.value?.comic != null || model.error.value != null }
                assertNull(model.error.value)
                assertEquals(20, model.reader.value!!.comic!!.images.size)
                assertEquals(9, model.reader.value!!.position.imageIndex)
                assertEquals("010.png", model.reader.value!!.position.imageEntry)
                assertEquals(.25f, model.reader.value!!.position.imageRatio, 0f)
                compose.onNodeWithText("10 / 20").assertIsDisplayed()
                compose.onRoot().performTouchInput { click(Offset(width * .9f, centerY)) }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
                waitForUi { model.reader.value?.position?.imageIndex == 10 }
                compose.onNodeWithText("11 / 20").assertIsDisplayed()
                compose.runOnIdle { model.mode("SCROLL") }
                waitForUi { model.reader.value?.preferences?.mode == "SCROLL" }
                compose.runOnIdle { model.seekComic(17, .3f) }
                waitForUi { model.reader.value?.comic?.cachedPage(17) != null && model.reader.value?.position?.imageIndex == 17 }
                assertEquals(160, model.reader.value!!.comic!!.cachedPage(17)!!.size.width)
                compose.runOnIdle { model.closeReader() }
                waitForUi { runBlocking { model.repository.dao.position(book.id)?.imageEntry == "018.png" } }
                compose.runOnIdle { model.open(book) }
                waitForUi { model.reader.value?.comic != null }
                assertEquals(20, model.reader.value!!.comic!!.images.size)
                assertEquals(17, model.reader.value!!.position.imageIndex)
                assertEquals("SCROLL", model.reader.value!!.preferences.mode)
                compose.runOnIdle { model.closeReader() }
            } finally { source.delete() }
        }
    }

    private fun waitForUi(condition: () -> Boolean) = compose.waitUntil(15000) { shadowOf(Looper.getMainLooper()).idle(); condition() }
}
