package com.chen.myviewer

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.chen.myviewer.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
class NovelMigrationTest {
    @Test fun schemaFourKeepsLegacyBookmarksAndCompletionAndAddsChapterTextPositions() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "novel-schema-migration.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(File(System.getProperty("myviewer.schemaDir"), "com.chen.myviewer.data.ViewerDatabase/4.json").readText()).getJSONObject("database")
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
            old.execSQL("INSERT INTO books (id,title,kind,storage,sourceUri,size,importedAt,lastOpenedAt,onShelf,inHistory,encoding,completed) VALUES ('old','旧小说','TEXT','DIRECT','content://old',100,1,2,1,1,'AUTO',1)")
            old.execSQL("INSERT INTO bookmarks (id,bookId,offset,chapter,snippet,note,createdAt) VALUES ('mark','old',123,'章节','正文','',1)")
            old.execSQL("INSERT INTO chapter_positions (bookId,chapterKey,imageIndex,imageRatio,progress,completed) VALUES ('old','chapter-one',2,0.4,1.0,1)")
            old.version = 4
        }
        val db = Room.databaseBuilder(context, ViewerDatabase::class.java, name).addMigrations(ViewerDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            assertTrue(dao.book("old")!!.completed)
            val chapter = dao.chapterPosition("old", "chapter-one")!!
            assertEquals(2, chapter.imageIndex)
            assertEquals(true, chapter.completed)
            assertEquals(0, chapter.textOffset)
            val position = ReadingPosition("old", chapterKey = "chapter-one", sessionId = "new")
            dao.putPosition(position)
            dao.saveReadingPosition(position.copy(textOffset = 777, sequence = 1))
            assertEquals(777, dao.chapterPosition("old", "chapter-one")!!.textOffset)
            assertEquals(true, dao.chapterPosition("old", "chapter-one")!!.completed)
            val marks = dao.chapterBookmarks("old", "").first()
            assertEquals(123, marks.single().offset)
        } finally { db.close(); context.deleteDatabase(name) }
        Unit
    }
}
