package com.chen.myviewer

import android.app.Application
import android.graphics.Bitmap
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import com.chen.myviewer.data.Book
import com.chen.myviewer.data.ViewerDatabase
import com.chen.myviewer.download.FolderDownloadRepository
import com.chen.myviewer.novel.NovelRepository
import com.chen.myviewer.novel.NovelBook
import com.chen.myviewer.novel.NovelChapter
import com.chen.myviewer.reader.comic.ComicArchive
import com.chen.myviewer.source.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderDownloadTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var database: ViewerDatabase
    private lateinit var sources: SourceRepository
    private lateinit var novels: NovelRepository
    private lateinit var exporter: FolderDownloadRepository
    private lateinit var provider: TestDownloadProvider
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        sources = SourceRepository(context, database.dao())
        novels = NovelRepository(context, database.dao())
        exporter = FolderDownloadRepository(context, sources, novels)
        provider = TestDownloadProvider().register(context)
    }
    @After fun cleanup() { Thread.interrupted(); database.close(); provider.directory.deleteRecursively() }

    private suspend fun title(): OnlineTitle {
        val chapters = listOf(SourceChapter("two", "继续", 2f, false, byteArrayOf()), SourceChapter("one", "开始", 1f, false, byteArrayOf()))
        val title = sources.saveTitle("offline.export", SourceManga("test", "下载测试", null, emptyList(), null, null, emptyList(), 2, chapters, byteArrayOf()))
        chapters.forEach { chapter ->
            val directory = sources.chapterDirectory(title.book.id, chapter.key).apply { mkdirs() }
            repeat(2) { index ->
                val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.GREEN)
                File(directory, "$index.img").outputStream().use { bitmap.compress(if (index == 0) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
                bitmap.recycle()
            }
            File(directory, "pages.json").writeText(JSONArray((0..1).map { JSONObject().put("url", "https://offline.invalid/$it") }).toString())
        }
        return title
    }

    @Test fun comicZipPreservesPageOrderFormatsAndDirectionAndCanBeImported() = runBlocking {
        val title = title()
        var saved = 0
        exporter.comic(title, title.manga.chapters!!.asReversed(), provider.tree) { _, count -> saved = count }
        assertEquals(2, saved)
        val files = provider.directory.listFiles()!!.sortedBy { it.name }
        assertEquals(2, files.size)
        assertTrue(files.first().name.contains("0001"))
        ZipFile(files.first()).use { zip ->
            assertEquals(listOf("ComicInfo.xml", "00001.png", "00002.jpg"), zip.entries().asSequence().map { it.name }.toList())
            assertTrue(zip.getInputStream(zip.getEntry("ComicInfo.xml")).bufferedReader().readText().contains("YesAndRightToLeft"))
        }
        ComicArchive.open(context, Book("export-import", "下载测试", "COMIC", "DIRECT", android.net.Uri.fromFile(files.first()).toString())).use { document ->
            assertEquals(2, document.imageNames.size)
            assertEquals("RTL", document.direction)
            assertEquals(60, document.size(1).height)
        }
        assertTrue(File(context.cacheDir, "folder-downloads").listFiles().orEmpty().isEmpty())
    }

    @Test fun failedLaterChapterKeepsCompletedZipAndCleansStaging() = runBlocking {
        val title = title()
        val chapters = title.manga.chapters!!.asReversed()
        sources.chapterDirectory(title.book.id, chapters.last().key).deleteRecursively()
        var saved = 0
        try { exporter.comic(title, chapters, provider.tree) { _, count -> saved = count }; fail("Second chapter must fail") }
        catch (_: Exception) { }
        assertEquals(1, saved)
        assertEquals(1, provider.directory.listFiles()!!.size)
        assertTrue(File(context.cacheDir, "folder-downloads").listFiles().orEmpty().isEmpty())
    }

    @Test fun sameNamesGetSuffixAndUnsafeOrLongNamesAreSanitized() {
        val original = File(provider.directory, "测试.txt").apply { writeText("原文件") }
        val temporary = File(context.cacheDir, "export-input").apply { writeText("新正文") }
        val folder = DocumentFile.fromTreeUri(context, provider.tree)!!
        exporter.publish(folder, temporary, "测试", "txt", "text/plain")
        assertEquals("原文件", original.readText())
        assertEquals("新正文", File(provider.directory, "测试 (2).txt").readText())
        assertEquals("_非法_名称__", FolderDownloadRepository.safeName("../非法:名称?\n"))
        assertTrue(FolderDownloadRepository.safeName("中".repeat(200)).toByteArray().size <= 180)
    }

    @Test fun providerWriteFailureDeletesOnlyNewDocument() {
        val original = File(provider.directory, "保留.txt").apply { writeText("保留") }
        val temporary = File(context.cacheDir, "export-input").apply { writeText("正文") }
        provider.failWrites = true
        try { exporter.publish(DocumentFile.fromTreeUri(context, provider.tree)!!, temporary, "失败", "txt", "text/plain"); fail("Write must fail") }
        catch (_: Exception) { }
        assertEquals(listOf(original.name), provider.directory.listFiles()!!.map { it.name })
    }

    @Test fun interruptedCopyDeletesPartialDocument() {
        val temporary = File(context.cacheDir, "export-input").apply { writeText("正文".repeat(100000)) }
        provider.interruptWrites = true
        try { exporter.publish(DocumentFile.fromTreeUri(context, provider.tree)!!, temporary, "取消", "txt", "text/plain"); fail("Copy must be interrupted") }
        catch (_: InterruptedException) { }
        finally { Thread.interrupted() }
        assertTrue(provider.directory.listFiles()!!.isEmpty())
    }

    @Test fun revokedFolderPermissionFailsBeforeDownloading() = runBlocking {
        val title = title()
        provider.writable = false
        try { exporter.comic(title, title.manga.chapters!!.take(1), provider.tree) { _, _ -> }; fail("Read-only folder must fail") }
        catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("重新选择")) }
        assertTrue(provider.directory.listFiles()!!.isEmpty())
    }

    @Test fun novelFailureLeavesNoPartialTxtAndRetryWritesOrderedUtf8() = runBlocking {
        val chapters = listOf(NovelChapter("第一章", "https://offline.invalid/one"), NovelChapter("第二章", "https://offline.invalid/two"))
        val title = novels.saveTitle("removed-source", NovelBook("https://offline.invalid/book", "原创导出小说", chapters = chapters))
        novels.chapterFile(title.book.id, chapters.first().url).apply { parentFile!!.mkdirs(); writeText("原创正文😀") }
        try { exporter.novel(title, chapters, provider.tree) { _, _ -> }; fail("Missing second chapter must fail") }
        catch (_: Exception) { }
        assertTrue(provider.directory.listFiles()!!.isEmpty())
        assertTrue(File(context.cacheDir, "folder-downloads").listFiles().orEmpty().isEmpty())
        novels.chapterFile(title.book.id, chapters.last().url).writeText("最后一章正文")
        exporter.novel(title, chapters, provider.tree) { _, _ -> }
        val output = provider.directory.listFiles()!!.single().readText(Charsets.UTF_8)
        assertEquals("原创导出小说\n\n第一章\n\n原创正文😀\n\n第二章\n\n最后一章正文\n\n", output)
    }

    @Test fun cancellingNovelBeforeCompletionCleansStagedText() = runBlocking {
        val chapters = listOf(NovelChapter("第一章", "https://offline.invalid/one"))
        val title = novels.saveTitle("removed-source", NovelBook("https://offline.invalid/book", "取消导出", chapters = chapters))
        try {
            exporter.novel(title, chapters, provider.tree) { _, _ -> throw kotlinx.coroutines.CancellationException("Cancel test") }
            fail("Must cancel")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertTrue(provider.directory.listFiles()!!.isEmpty())
        assertTrue(File(context.cacheDir, "folder-downloads").listFiles().orEmpty().isEmpty())
    }
}
