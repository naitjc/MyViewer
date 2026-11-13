package com.chen.myviewer

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import com.chen.myviewer.data.ViewerDatabase
import com.chen.myviewer.source.*
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AidokuSourceTest {
    private val entry = SourceEntry("en.guya", "Guya", 2, listOf("en"), "https://example.test/guya.aix", "https://guya.cubari.moe", 0)
    private val metadata = """{"slug":"sample","title":"示例漫画","description":"<b>原创测试简介</b>","author":"Test Author","groups":{"1":"Test"},"chapters":{"1":{"folder":"chapter-one","title":"开始","groups":{"1":["1.png","2.png"]}},"2":{"folder":"chapter-two","title":"继续","groups":{"1":["3.png"]}}}}"""

    @Test fun realSourceSearchDetailsPagesDownloadAndOfflineReading() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        val requests = mutableListOf<String>()
        var offline = false
        val image = ByteArrayOutputStream().also { output ->
            val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GREEN)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
        }.toByteArray()
        val http = SourceHttp(context, Interceptor { chain ->
            check(!offline) { "Offline test must not issue network requests" }
            val request = chain.request()
            requests += request.url.encodedPath
            val body = when {
                request.url.encodedPath == "/api/get_all_series/" -> """{"示例漫画":{"slug":"sample","last_updated":1}}""".toByteArray()
                request.url.encodedPath == "/api/series/sample/" -> metadata.toByteArray()
                else -> {
                    assertEquals("Aidoku", request.header("User-Agent"))
                    image
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        })
        try {
            val repository = SourceRepository(context, db.dao(), http)
            repository.installPackage(entry, javaClass.getResourceAsStream("/aidoku/en.guya-v2.aix")!!.use { it.readBytes() })
            val result = repository.search(entry.id, "示例", 1)
            assertEquals("示例漫画", result.entries.single().title)
            assertFalse(result.hasNextPage)
            val manga = repository.details(entry.id, result.entries.single())
            assertEquals("原创测试简介", manga.description)
            assertEquals(2, manga.chapters!!.size)
            assertEquals(2, manga.viewer)
            val title = repository.saveTitle(entry.id, manga)
            val chapter = manga.chapters.last()
            val pages = repository.chapterPages(title, chapter)
            assertEquals(2, pages.size)
            OnlineComicDocument(repository, title, chapter, pages).use { document ->
                assertEquals("RTL", document.direction)
                assertEquals(64, document.size(0).width)
                assertEquals(128, document.page(0).size.height)
            }
            val provider = TestDownloadProvider().register(context)
            val exports = com.chen.myviewer.download.FolderDownloadRepository(context, repository, com.chen.myviewer.novel.NovelRepository(context, db.dao()))
            exports.comic(title, listOf(chapter), provider.tree) { _, _ -> }
            assertEquals(1, provider.directory.listFiles()!!.size)
            repository.download(title, chapter) { _, _ -> }
            assertTrue(repository.downloaded(title.book.id, chapter.key))
            repository.uninstall(entry.id)
            repository.uninstall(entry.id)
            assertFalse(repository.installed().any { it.id == entry.id })
            val requestCount = requests.size
            offline = true
            File(context.cacheDir, "online").deleteRecursively()
            val reopened = SourceRepository(context, db.dao(), http)
            val saved = reopened.title(db.dao().book(title.book.id)!!)
            val offlinePages = reopened.chapterPages(saved, saved.manga.chapters!!.last())
            OnlineComicDocument(reopened, saved, chapter, offlinePages).use { document ->
                assertEquals(128, document.page(1).size.height)
                assertEquals(1, document.restoreIndex("000001", 0))
            }
            exports.comic(saved, listOf(chapter), provider.tree) { _, _ -> }
            assertEquals(2, provider.directory.listFiles()!!.size)
            assertEquals(requestCount, requests.size)
            assertTrue(db.dao().book(title.book.id)!!.onShelf)
            repository.installPackage(entry, javaClass.getResourceAsStream("/aidoku/en.guya-v2.aix")!!.use { it.readBytes() })
            assertTrue(repository.installed().any { it.id == entry.id })
            try { repository.uninstall("../sources"); fail("Unsafe source ID must fail") }
            catch (_: IllegalArgumentException) { }
        } finally { db.close() }
    }

    @Test fun invalidPackageLeavesInstalledSourceUsableAndCatalogResolvesRelativeUrls() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = SourceRepository(context, db.dao())
            val valid = javaClass.getResourceAsStream("/aidoku/en.guya-v2.aix")!!.use { it.readBytes() }
            repository.installPackage(entry, valid)
            val before = repository.installed().single { it.id == entry.id }
            try { repository.installPackage(entry.copy(version = 3), valid); fail("Mismatched source version must fail") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before.directory, repository.installed().single { it.id == entry.id }.directory)
            val catalog = SourceRepository.parseCatalog("""{"sources":[{"id":"en.example","name":"Example","version":1,"downloadURL":"sources/example.aix","languages":["en"]}]}""", "https://example.test/repo/index.min.json")
            assertEquals("https://example.test/repo/sources/example.aix", catalog.single().downloadUrl)
            assertEquals("https://example.test/repo/index.min.json", SourceRepository.normalizeIndexUrl("https://example.test/repo/"))
        } finally { db.close() }
    }
    @Test fun chapterProgressRejectsSavesFromPreviousSessions() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            dao.putBook(com.chen.myviewer.data.Book("series", "Test", "COMIC", "ONLINE", "myviewer://series"))
            val first = com.chen.myviewer.data.ReadingPosition("series", sessionId = "first", chapterKey = "one")
            dao.putPosition(first)
            dao.saveReadingPosition(first.copy(imageIndex = 1, progress = .5f, sequence = 1))
            val second = first.copy(sessionId = "second", chapterKey = "two")
            dao.putPosition(second)
            dao.saveReadingPosition(first.copy(imageIndex = 99, progress = 1f, sequence = 99))
            dao.saveReadingPosition(second.copy(imageIndex = 2, progress = .75f, sequence = 1))
            assertEquals(1, dao.chapterPosition("series", "one")!!.imageIndex)
            assertEquals(2, dao.chapterPosition("series", "two")!!.imageIndex)
            assertEquals("two", dao.position("series")!!.chapterKey)
        } finally { db.close() }
    }

}
