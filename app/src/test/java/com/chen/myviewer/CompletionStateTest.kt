package com.chen.myviewer

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.chen.myviewer.data.*
import com.chen.myviewer.source.sourceLanguageGroup
import com.chen.myviewer.source.sourceLanguageLabel
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
class CompletionStateTest {
    @Test fun completionChangesOnlyTheRequestedBookOrChapterAndSurvivesReadingSaves() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            dao.putBook(Book("one", "故事", "COMIC", "ONLINE", "myviewer://one"))
            dao.putBook(Book("two", "另一本", "TEXT", "DIRECT", "content://two"))
            val position = ReadingPosition("one", imageIndex = 2, progress = .3f, sessionId = "current", chapterKey = "chapter-one")
            dao.putPosition(position)
            dao.setCompleted("one", true)
            dao.setChapterCompleted("one", "chapter-one", true)
            dao.saveReadingPosition(position.copy(imageIndex = 3, progress = .4f, sequence = 1))
            assertTrue(dao.book("one")!!.completed)
            assertFalse(dao.book("two")!!.completed)
            assertEquals(true, dao.chapterPosition("one", "chapter-one")!!.completed)
            assertEquals(3, dao.chapterPosition("one", "chapter-one")!!.imageIndex)
            assertNull(dao.chapterPosition("one", "chapter-two"))
            dao.setChapterCompleted("one", "chapter-one", false)
            dao.saveReadingPosition(position.copy(progress = 1f, sequence = 2))
            assertEquals(false, dao.chapterPosition("one", "chapter-one")!!.completed)
            dao.setChapterCompleted("one", "chapter-two", true)
            dao.setCompleted("one", false)
            assertFalse(dao.book("one")!!.completed)
            assertEquals(true, dao.chapterPosition("one", "chapter-two")!!.completed)
            assertEquals(1f, dao.position("one")!!.progress)
        } finally { db.close() }
    }

    @Test fun completionRejectsStaleSessionsAndRequiresEveryKnownChapter() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            dao.putBook(Book("series", "故事", "COMIC", "ONLINE", "myviewer://series"))
            val old = ReadingPosition("series", sessionId = "old", chapterKey = "one")
            val current = old.copy(sessionId = "current", chapterKey = "two")
            dao.putPosition(current)
            dao.finishReading(old.copy(progress = 1f, sequence = 99), listOf("one", "two"))
            assertNull(dao.chapterPosition("series", "one"))
            assertFalse(dao.book("series")!!.completed)
            dao.finishReading(current.copy(progress = 1f, sequence = 1), listOf("one", "two"))
            assertEquals(true, dao.chapterPosition("series", "two")!!.completed)
            assertFalse(dao.book("series")!!.completed)
            val next = current.copy(sessionId = "next", chapterKey = "one")
            dao.putPosition(next)
            dao.finishReading(next.copy(progress = 1f, sequence = 1), listOf("one", "two"))
            assertTrue(dao.book("series")!!.completed)
        } finally { db.close() }
    }

    @Test fun schemaThreeUpgradesAndPersistsManualCompletionWithoutLosingExistingData() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "completion-upgrade.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(File(System.getProperty("myviewer.schemaDir"), "com.chen.myviewer.data.ViewerDatabase/3.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                repeat(indices?.length() ?: 0) { j -> old.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { old.execSQL(setup.getString(it)) }
            old.execSQL("INSERT INTO books (id,title,kind,storage,sourceUri,size,importedAt,lastOpenedAt,onShelf,inHistory,encoding) VALUES ('old','旧漫画','COMIC','ONLINE','myviewer://old',123,1,2,1,1,'AUTO')")
            old.execSQL("INSERT INTO positions (bookId,textOffset,imageIndex,imageEntry,imageRatio,progress,sessionId,sequence,savedAt,chapterKey) VALUES ('old',0,2,'000002',0.5,0.3,'old-session',4,5,'chapter-one')")
            old.execSQL("INSERT INTO chapter_positions (bookId,chapterKey,imageIndex,imageRatio,progress) VALUES ('old','chapter-one',2,0.5,1.0)")
            old.execSQL("INSERT INTO reading_times (sessionId,day,bookId,durationMs) VALUES ('old-session','2026-11-11','old',1200)")
            old.version = 3
        }
        fun open() = Room.databaseBuilder(context, ViewerDatabase::class.java, name)
            .addMigrations(ViewerDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        val upgraded = open()
        try {
            val dao = upgraded.dao()
            assertFalse(dao.book("old")!!.completed)
            assertNull(dao.chapterPosition("old", "chapter-one")!!.completed)
            assertEquals(2, dao.position("old")!!.imageIndex)
            assertEquals(.5f, dao.chapterPosition("old", "chapter-one")!!.imageRatio)
            assertEquals(1200L, dao.observeReadingTotals("2026-11-11").first().totalMs)
            dao.setCompleted("old", true)
            dao.setChapterCompleted("old", "chapter-one", false)
        } finally { upgraded.close() }
        val reopened = open()
        try {
            assertTrue(reopened.dao().book("old")!!.completed)
            assertEquals(false, reopened.dao().chapterPosition("old", "chapter-one")!!.completed)
            assertEquals(1f, reopened.dao().chapterPosition("old", "chapter-one")!!.progress)
        } finally { reopened.close() }
        context.deleteDatabase(name)
        Unit
    }

    @Test fun languageFiltersGroupScriptRegionAndCaseVariants() {
        assertEquals("zh", sourceLanguageGroup("zh-Hant"))
        assertEquals("zh", sourceLanguageGroup("zh_Hans"))
        assertEquals("en", sourceLanguageGroup("EN-us"))
        assertEquals("pt", sourceLanguageGroup("pt-BR"))
        assertEquals("中文", sourceLanguageLabel("zh"))
        assertTrue(sourceLanguageLabel("ja").isNotBlank())
    }
}
