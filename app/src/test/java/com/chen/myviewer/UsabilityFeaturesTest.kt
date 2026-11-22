package com.chen.myviewer

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.chen.myviewer.data.*
import com.chen.myviewer.ui.formatFileSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UsabilityFeaturesTest {
    @Test
    fun schemaFiveToSixAddsPinnedAndUnreadChaptersWithDefaults() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "schema-5-to-6.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(
            File(System.getProperty("myviewer.schemaDir"), "com.chen.myviewer.data.ViewerDatabase/5.json").readText()
        ).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                repeat(indices?.length() ?: 0) { j ->
                    old.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { old.execSQL(setup.getString(it)) }
            old.execSQL("INSERT INTO books (id,title,kind,storage,sourceUri,size,importedAt,lastOpenedAt,onShelf,inHistory,encoding,completed) VALUES ('b1','旧书','TEXT','DIRECT','content://b1',100,1,2,1,1,'AUTO',0)")
            old.version = 5
        }
        val db = Room.databaseBuilder(context, ViewerDatabase::class.java, name)
            .addMigrations(ViewerDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.dao()
            val book = dao.book("b1")!!
            assertFalse(book.pinned)
            assertEquals(0, book.unreadChapters)

            dao.setPinned("b1", true)
            dao.setUnreadChapters("b1", 3)
            dao.rename("b1", "新书名")
            val updated = dao.book("b1")!!
            assertTrue(updated.pinned)
            assertEquals(3, updated.unreadChapters)
            assertEquals("新书名", updated.title)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test
    fun backupRoundTripRestoresLibraryPositionsBookmarksTimesAndSettings() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val sourceDbName = "backup-source.db"
        val targetDbName = "backup-target.db"
        context.deleteDatabase(sourceDbName)
        context.deleteDatabase(targetDbName)
        val sourceDb = Room.databaseBuilder(context, ViewerDatabase::class.java, sourceDbName).allowMainThreadQueries().build()
        val targetDb = Room.databaseBuilder(context, ViewerDatabase::class.java, targetDbName).allowMainThreadQueries().build()
        val sourcePrefsFile = File(context.cacheDir, "backup-src-${System.nanoTime()}.preferences_pb")
        val targetPrefsFile = File(context.cacheDir, "backup-dst-${System.nanoTime()}.preferences_pb")
        try {
            val sourceStore = SettingsStore(PreferenceDataStoreFactory.create { sourcePrefsFile })
            val targetStore = SettingsStore(PreferenceDataStoreFactory.create { targetPrefsFile })
            sourceStore.update(ReaderSettings(fontSize = 24f, background = "NIGHT", immersiveMode = true, showFooterBar = false))
            sourceStore.setLibrarySort(LibrarySort.TITLE)
            sourceStore.setLibraryColumns(4)

            val sDao = sourceDb.dao()
            sDao.putBook(Book("bk1", "测试小说", "TEXT", "COPY", "content://bk1", pinned = true, completed = true, inHistory = true, unreadChapters = 2))
            sDao.putPosition(ReadingPosition("bk1", textOffset = 450, progress = 0.45f, sessionId = "s1", sequence = 3, chapterKey = "ch1"))
            sDao.putChapterPosition(ChapterPosition("bk1", "ch1", 2, 0.5f, 1.0f, completed = true, textOffset = 450))
            sDao.putPreferences(BookPreferences("bk1", mode = "SCROLL", direction = "RTL"))
            sDao.putBookmark(Bookmark("mk1", "bk1", 450, "第一章", "摘录片段", chapterKey = "ch1"))
            sDao.saveReadingTimes(listOf(ReadingTimeRecord("s1", "2026-11-22", "bk1", 12_500L)))

            val json = BackupManager.exportJson(sDao, sourceStore)
            val summary = BackupManager.importJson(json, targetDb.dao(), targetStore)
            assertEquals(1, summary.books)
            assertEquals(1, summary.bookmarks)
            assertEquals(1, summary.readingTimes)

            val tDao = targetDb.dao()
            val restoredBook = tDao.book("bk1")!!
            assertEquals("测试小说", restoredBook.title)
            assertTrue(restoredBook.pinned)
            assertTrue(restoredBook.completed)
            assertEquals(2, restoredBook.unreadChapters)
            assertEquals(450, tDao.position("bk1")!!.textOffset)
            assertEquals(true, tDao.chapterPosition("bk1", "ch1")!!.completed)
            assertEquals("SCROLL", tDao.preferences("bk1")!!.mode)
            assertEquals("摘录片段", tDao.bookmarks("bk1").first().single().snippet)
            assertEquals(12_500L, tDao.observeReadingTotals("2026-11-22").first().totalMs)

            val restoredSettings = targetStore.settings.first()
            assertEquals(24f, restoredSettings.fontSize)
            assertEquals("NIGHT", restoredSettings.background)
            assertTrue(restoredSettings.immersiveMode)
            assertFalse(restoredSettings.showFooterBar)
            assertEquals(LibrarySort.TITLE, targetStore.librarySort.first())
            assertEquals(4, targetStore.libraryColumns.first())
        } finally {
            sourceDb.close()
            targetDb.close()
            context.deleteDatabase(sourceDbName)
            context.deleteDatabase(targetDbName)
            sourcePrefsFile.delete()
            targetPrefsFile.delete()
        }
        Unit
    }

    @Test
    fun batchDaoAndHistoryRemoveOperationsWorkAsExpected() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "batch-ops.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, ViewerDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            dao.putBook(Book("b1", "一", "TEXT", "DIRECT", "content://1", onShelf = true, inHistory = true))
            dao.putBook(Book("b2", "二", "COMIC", "DIRECT", "content://2", onShelf = true, inHistory = true))

            dao.setCompletedBatch(listOf("b1", "b2"), true)
            assertTrue(dao.book("b1")!!.completed)
            assertTrue(dao.book("b2")!!.completed)

            dao.removeFromHistory("b1")
            assertFalse(dao.book("b1")!!.inHistory)
            assertTrue(dao.book("b2")!!.inHistory)

            dao.hideBatch(listOf("b1", "b2"))
            assertFalse(dao.book("b1")!!.onShelf)
            assertFalse(dao.book("b2")!!.onShelf)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test
    fun formatFileSizeFormatsUnitsCleanly() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("1.5 KB", formatFileSize(1536L))
        assertEquals("2.0 MB", formatFileSize(2L * 1024 * 1024))
        assertEquals("1.50 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
