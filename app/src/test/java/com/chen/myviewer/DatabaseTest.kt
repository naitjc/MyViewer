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
    @Test fun cumulativeTimeIsIdempotentAndSurvivesHistoryAndBookUpdates() = runBlocking {
        insert(); dao.putBook(Book("two", "漫画", "COMIC", "DIRECT", "content://book/two"))
        val first = ReadingTimeRecord("session-one", "2026-10-29", "one", 1000)
        dao.saveReadingTimes(listOf(first, first))
        dao.saveReadingTimes(listOf(first.copy(durationMs = 500)))
        assertEquals(1000, dao.observeReadingTotals("2026-10-29").first().totalMs)
        dao.saveReadingTimes(listOf(first.copy(day = "2026-10-30", durationMs = 2000),
            first.copy(sessionId = "another", durationMs = 4000),
            ReadingTimeRecord("session-two", "2026-10-29", "two", 5000)))
        assertEquals(ReadingTotals(10000, 12000), dao.observeReadingTotals("2026-10-29").first())
        assertEquals(mapOf("one" to 7000L, "two" to 5000L), dao.observeBookReadingTimes().first().associate { it.bookId to it.totalMs })
        dao.clearHistory(); dao.shelf("one", false); dao.putBook(dao.book("one")!!.copy(title = "改名后的书"))
        assertEquals(12000, dao.observeReadingTotals("2026-10-29").first().totalMs)
    }
}
