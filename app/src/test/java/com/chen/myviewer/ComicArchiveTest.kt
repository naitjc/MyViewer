package com.chen.myviewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.chen.myviewer.data.Book
import com.chen.myviewer.reader.comic.ComicArchive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicArchiveTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun twentyOriginalsAndTwentyNamedThumbnailsCountAsTwentyPages() {
        val entries = linkedMapOf<String, Pair<Int, Int>>()
        repeat(20) { index ->
            val name = (index + 1).toString().padStart(3, '0')
            entries["$name.png"] = 160 to 320
            entries["${name}_thumb.png"] = 40 to 80
        }
        withArchive(entries) { archive ->
            assertEquals(20, archive.images.size)
            assertEquals((1..20).map { "${it.toString().padStart(3, '0')}.png" }, archive.images.map { it.name })
            archive.images.indices.forEach { assertEquals(160, archive.size(it).width) }
            assertEquals(9, archive.restoreIndex("010_thumb.png", 19))
            assertEquals(9, archive.restoreIndex("010.png", 19))
        }
    }

    @Test fun thumbnailFoldersMatchWithinEachChapterAndSupportDifferentExtensions() {
        withArchive(linkedMapOf(
            "chapter1/001.png" to (160 to 320),
            "chapter1/Thumbnails/001.jpg" to (40 to 80),
            "chapter2/001.png" to (160 to 320),
            "chapter2/Thumbnails/001.jpg" to (40 to 80),
            "chapter2/002.png" to (160 to 320),
        )) { archive ->
            assertEquals(listOf("chapter1/001.png", "chapter2/001.png", "chapter2/002.png"), archive.images.map { it.name })
            assertEquals(1, archive.restoreIndex("chapter2/Thumbnails/001.jpg", 0))
        }
    }

    @Test fun trailingTThumbnailsUseTheirCorrespondingOriginalsAndRestoreOldPositions() {
        val entries = linkedMapOf<String, Pair<Int, Int>>()
        repeat(20) { index ->
            val name = (index + 1).toString().padStart(3, '0')
            entries["$name.png"] = 160 to 320
            entries["${name}t.png"] = 40 to 80
        }
        withArchive(entries) { archive ->
            assertEquals(20, archive.images.size)
            assertEquals((1..20).map { "${it.toString().padStart(3, '0')}.png" }, archive.images.map { it.name })
            assertEquals(9, archive.restoreIndex("010t.png", 19))
        }
    }

    @Test fun trailingTMatchingRespectsChaptersCaseAndOriginalNamesEndingInT() {
        withArchive(linkedMapOf(
            "chapter1/001.png" to (160 to 320), "chapter1/001T.jpg" to (40 to 80),
            "chapter2/001.png" to (160 to 320), "chapter2/001t.png" to (40 to 80),
            "forest.png" to (160 to 320), "forestt.png" to (40 to 80),
            "thumbs/forestt.png" to (40 to 80),
            "unmatchedt.png" to (40 to 80), "chapter3/001t.png" to (40 to 80),
        )) { archive ->
            assertEquals(listOf("chapter1/001.png", "chapter2/001.png", "chapter3/001t.png", "forest.png", "unmatchedt.png"), archive.images.map { it.name })
            assertEquals(3, archive.restoreIndex("forestt.png", 0))
            assertEquals(3, archive.restoreIndex("thumbs/forestt.png", 0))
        }
    }

    @Test fun trailingTIncludesCroppedThumbnailsAndSameSizeLowerQualityJpeg() {
        val image = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image); val paint = Paint()
        for (y in 0 until 320 step 7) for (x in 0 until 160 step 7) {
            paint.color = Color.rgb((x * 17 + y * 3) % 256, (x * 7 + y * 13) % 256, (x * 11 + y * 19) % 256)
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + 7).toFloat(), (y + 7).toFloat(), paint)
        }
        fun jpeg(quality: Int) = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        val full = jpeg(95); val small = jpeg(10); image.recycle()
        assertTrue(small.size < full.size)
        withArchive(linkedMapOf(
            "001.jpg" to (160 to 320), "001t.jpg" to (160 to 320),
            "002.png" to (160 to 320), "002t.png" to (80 to 80),
        ), mapOf("001.jpg" to full, "001t.jpg" to small)) { archive ->
            assertEquals(listOf("001.jpg", "002.png"), archive.images.map { it.name })
        }
    }

    @Test fun unmatchedAmbiguousOrOnlyThumbnailImagesRemainReadable() {
        withArchive(linkedMapOf(
            "thumbs/001.png" to (40 to 80),
            "chapter1/002.png" to (160 to 320),
            "chapter2/002.png" to (160 to 320),
            "thumbs/002.png" to (40 to 80),
            "003.png" to (160 to 320),
            "003_thumb.png" to (80 to 80),
            "004.png" to (40 to 80),
            "004_thumb.png" to (160 to 320),
        )) { archive -> assertEquals(8, archive.images.size) }
        withArchive(linkedMapOf("thumbnails/001.png" to (40 to 80))) { archive ->
            assertEquals("thumbnails/001.png", archive.images.single().name)
        }
    }

    @Test fun legitimateRepeatedSmallPagesAndChapterNamesAreNotDeduplicated() {
        withArchive(linkedMapOf(
            "001.png" to (160 to 320), "002.png" to (160 to 320),
            "003.png" to (40 to 80), "chapter1/001.png" to (160 to 320),
            "chapter2/001.png" to (160 to 320), "preview/chapter3/001.png" to (160 to 320),
        )) { archive -> assertEquals(6, archive.images.size) }
    }

    @Test fun thumbnailPrefixesAndExactCopiesUseOriginalButNeverMatchOtherChapters() {
        withArchive(linkedMapOf(
            "pages/001.png" to (160 to 320), "thumbs/001.png" to (40 to 80),
            "002.png" to (160 to 320), "preview-002.png" to (40 to 80),
            "003.png" to (160 to 320), "003.thumbnail.png" to (160 to 320),
            "chapter1/004.png" to (160 to 320), "chapter2/thumbs/004.png" to (40 to 80),
        )) { archive ->
            assertEquals(listOf("002.png", "003.png", "chapter1/004.png", "chapter2/thumbs/004.png", "pages/001.png"), archive.images.map { it.name })
            assertEquals(4, archive.restoreIndex("thumbs/001.png", 0))
            assertEquals(4, archive.restoreIndex("missing.png", 500))
        }
    }

    private fun withArchive(entries: Map<String, Pair<Int, Int>>, contents: Map<String, ByteArray> = emptyMap(), check: (ComicArchive) -> Unit) {
        val id = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "$id.zip")
        try {
            TestComicZip.write(file, entries, contents)
            val book = Book(id, "缩略图测试", "COMIC", "DIRECT", Uri.fromFile(file).toString())
            ComicArchive.open(context, book).use(check)
        } finally { file.delete(); File(context.cacheDir, "comic/$id").deleteRecursively() }
    }
}

internal object TestComicZip {
    fun write(file: File, entries: Map<String, Pair<Int, Int>>, contents: Map<String, ByteArray> = emptyMap()) {
        val bytes = entries.values.distinct().associateWith { (width, height) ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 150, 180)) }
            try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
            finally { bitmap.recycle() }
        }
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, dimensions) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(contents[name] ?: bytes.getValue(dimensions)); zip.closeEntry()
            }
        }
    }
}
