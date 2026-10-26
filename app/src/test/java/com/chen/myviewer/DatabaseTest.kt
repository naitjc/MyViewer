package com.chen.myviewer

import android.app.Application
import androidx.room.Room
import com.chen.myviewer.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DatabaseTest {
    private lateinit var database: ViewerDatabase
    private lateinit var dao: ViewerDao
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ViewerDatabase::class.java).allowMainThreadQueries().build(); dao = database.dao() }
    @After fun tearDown() { database.close() }
    private suspend fun insert() { dao.putBook(Book("one", "故事", "TEXT", "DIRECT", "content://book/one")) }
    @Test fun staleSessionCannotOverwriteNewReadingPosition() = runBlocking {
        insert(); dao.putPosition(ReadingPosition("one", sessionId = "current"))
        dao.savePosition("one", "current", 600, 0, "", 0f, .6f, 2, 10)
        dao.savePosition("one", "old", 100, 0, "", 0f, .1f, 100, 11)
        dao.savePosition("one", "current", 200, 0, "", 0f, .2f, 1, 12)
        assertEquals(600, dao.position("one")!!.textOffset)
    }
    @Test fun clearingHistoryOrHidingShelfRetainsProgressAndBookmarks() = runBlocking {
        insert(); dao.opened("one", 100)
        dao.putPosition(ReadingPosition("one", textOffset = 123))
        dao.putBookmark(Bookmark("bookmark", "one", 100, "第一章", "一些内容"))
        dao.clearHistory(); dao.shelf("one", false)
        assertFalse(dao.book("one")!!.inHistory); assertFalse(dao.book("one")!!.onShelf)
        assertEquals(123, dao.position("one")!!.textOffset)
        assertEquals(1, dao.bookmarks("one").first().size)
    }
    @Test fun upsertBookDoesNotCascadeDeleteProgress() = runBlocking {
        insert(); dao.putPosition(ReadingPosition("one", textOffset = 456))
        dao.putBook(dao.book("one")!!.copy(title = "新的标题"))
        assertEquals(456, dao.position("one")!!.textOffset)
    }
}
