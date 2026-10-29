package com.chen.myviewer

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.chen.myviewer.data.*
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
class ReadingTimeMigrationTest {
    @Test fun versionOneUpgradesWithoutLosingBooksPositionsBookmarksOrPreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "reading-time-upgrade.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(File(System.getProperty("myviewer.schemaDir"), "com.chen.myviewer.data.ViewerDatabase/1.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { i ->
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                repeat(indices?.length() ?: 0) { j -> old.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { old.execSQL(setup.getString(it)) }
            old.execSQL("INSERT INTO books (id,title,kind,storage,sourceUri,size,importedAt,lastOpenedAt,onShelf,inHistory,encoding) VALUES ('old','旧小说','TEXT','DIRECT','content://old',123,1,2,1,1,'UTF-8')")
            old.execSQL("INSERT INTO positions (bookId,textOffset,imageIndex,imageEntry,imageRatio,progress,sessionId,sequence,savedAt) VALUES ('old',123,2,'02.jpg',0.5,0.3,'old-session',4,5)")
            old.execSQL("INSERT INTO book_preferences (bookId,mode,direction) VALUES ('old','SCROLL','RTL')")
            old.execSQL("INSERT INTO bookmarks (id,bookId,offset,chapter,snippet,createdAt,note) VALUES ('mark','old',12,'第一章','原书签',1,'保留')")
            old.execSQL("INSERT INTO folders (uri,name,recursive) VALUES ('content://folder','旧文件夹',1)")
            old.version = 1
        }
        fun open() = Room.databaseBuilder(context, ViewerDatabase::class.java, name).addMigrations(ViewerDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        val upgraded = open()
        try {
            val db = upgraded
            val dao = db.dao()
            assertEquals("旧小说", dao.book("old")!!.title)
            assertEquals(123, dao.position("old")!!.textOffset)
            assertEquals("02.jpg", dao.position("old")!!.imageEntry)
            assertEquals(.5f, dao.position("old")!!.imageRatio, 0f)
            assertEquals("SCROLL", dao.preferences("old")!!.mode)
            assertEquals("RTL", dao.preferences("old")!!.direction)
            assertEquals("保留", dao.bookmarks("old").first().single().note)
            assertEquals(1, dao.bookmarks("old").first().single().createdAt)
            assertEquals("旧文件夹", dao.folders().first().single().name)
            assertEquals(ReadingTotals(), dao.observeReadingTotals("2026-10-29").first())
            dao.saveReadingTimes(listOf(ReadingTimeRecord("new-session", "2026-10-29", "old", 1200)))
        } finally { upgraded.close() }
        val reopened = open()
        try { assertEquals(1200, reopened.dao().observeReadingTotals("2026-10-29").first().totalMs) }
        finally { reopened.close() }
        context.deleteDatabase(name)
        Unit
    }
}
