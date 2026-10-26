package com.chen.myviewer

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.chen.myviewer.data.*
import com.chen.myviewer.reader.comic.ComicArchive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImportRepositoryTest {
    private lateinit var db: ViewerDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var directory: File
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        repo = LibraryRepository(context, db.dao())
        directory = File(context.cacheDir, "test-input").apply { mkdirs() }
    }
    @After fun cleanup() { db.close(); directory.deleteRecursively() }
    private fun candidate(file: File) = ImportCandidate(Uri.fromFile(file), file.name, file.length())
    @Test fun copiedNovelSurvivesDeletionOfOriginal() = runBlocking {
        val source = File(directory, "故事.txt").apply { writeText("第一章 归来\n雨停了，站台上还有一盏灯。") }
        val result = repo.import(listOf(candidate(source)), true) { _, _ -> }
        assertEquals(1, result.added)
        val book = db.dao().observeLibrary().first().single().book
        assertEquals("COPY", book.storage)
        source.delete()
        assertTrue(repo.loadText(book).text.contains("雨停了"))
    }
    @Test fun repeatedImportKeepsSameBookAndReadingPosition() = runBlocking {
        val source = File(directory, "故事.txt").apply { writeText("第一章\n正文内容") }
        val selection = listOf(candidate(source))
        repo.import(selection, false) { _, _ -> }
        val book = db.dao().observeLibrary().first().single().book
        db.dao().putPosition(ReadingPosition(book.id, textOffset = 5))
        db.dao().shelf(book.id, false)
        val result = repo.import(selection, false) { _, _ -> }
        assertEquals(1, result.existing); assertEquals(0, result.added)
        assertEquals(book.id, db.dao().observeLibrary().first().single().book.id)
        assertEquals(5, db.dao().position(book.id)!!.textOffset)
        assertTrue(db.dao().book(book.id)!!.onShelf)
    }
    @Test fun zipOrdersImagesReadsMetadataAndIgnoresAuxiliaryPaths() = runBlocking {
        val file = File(directory, "漫画.zip")
        val png = png(40, 60)
        ZipOutputStream(file.outputStream()).use { zip ->
            listOf("10.png", "2.png", "__MACOSX/._2.png", "../escape.png").forEach { name -> zip.putNextEntry(ZipEntry(name)); zip.write(png); zip.closeEntry() }
            zip.putNextEntry(ZipEntry("ComicInfo.xml")); zip.write("<ComicInfo><Manga>YesAndRightToLeft</Manga></ComicInfo>".toByteArray()); zip.closeEntry()
        }
        val book = Book("comic", "漫画", "COMIC", "DIRECT", Uri.fromFile(file).toString())
        ComicArchive.open(context, book).use {
            assertEquals(listOf("2.png", "10.png"), it.images.map { image -> image.name })
            assertEquals("RTL", it.direction)
            assertEquals(40, it.size(0).width)
            assertTrue(it.imageFile(0).canonicalPath.startsWith(File(context.cacheDir, "comic/comic").canonicalPath))
        }
        assertFalse(File(context.cacheDir, "escape.png").exists())
    }
    @Test fun invalidCopiedZipDoesNotCreateBookOrLeaveSourceCopy() = runBlocking {
        val source = File(directory, "没有图片.zip")
        ZipOutputStream(source.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("readme.txt")); zip.write("hello".toByteArray()); zip.closeEntry() }
        val before = File(context.filesDir, "books").listFiles()?.map { it.name }?.toSet().orEmpty()
        val result = repo.import(listOf(candidate(source)), true) { _, _ -> }
        assertEquals(0, result.added); assertEquals(1, result.failures.size)
        assertTrue(db.dao().observeLibrary().first().isEmpty())
        val after = File(context.filesDir, "books").listFiles()?.map { it.name }?.toSet().orEmpty()
        assertEquals(before, after); assertTrue(source.exists())
    }
    private fun png(width: Int, height: Int): ByteArray {
        val output = ByteArrayOutputStream(); val data = DataOutputStream(output)
        data.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun chunk(name: String, content: ByteArray) {
            val type = name.toByteArray(Charsets.US_ASCII)
            data.writeInt(content.size); data.write(type); data.write(content)
            val crc = CRC32().apply { update(type); update(content) }; data.writeInt(crc.value.toInt())
        }
        val header = ByteArrayOutputStream().also { val h = DataOutputStream(it); h.writeInt(width); h.writeInt(height); h.write(byteArrayOf(8, 2, 0, 0, 0)) }.toByteArray()
        chunk("IHDR", header)
        val pixels = ByteArrayOutputStream().also { out -> repeat(height) { out.write(0); repeat(width) { out.write(byteArrayOf(90, 120, 100)) } } }.toByteArray()
        val compressed = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(pixels) } }.toByteArray()
        chunk("IDAT", compressed); chunk("IEND", byteArrayOf()); return output.toByteArray()
    }
}
