package com.chen.myviewer.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books", indices = [Index("sourceUri", unique = true)])
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val storage: String,
    val sourceUri: String,
    val localPath: String? = null,
    val coverPath: String? = null,
    val size: Long = 0,
    val importedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0,
    val onShelf: Boolean = true,
    val inHistory: Boolean = false,
    val encoding: String = "AUTO",
)

@Entity(tableName = "positions", foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class ReadingPosition(
    @PrimaryKey val bookId: String,
    val textOffset: Int = 0,
    val imageIndex: Int = 0,
    val imageEntry: String = "",
    val imageRatio: Float = 0f,
    val progress: Float = 0f,
    val sessionId: String = "",
    val sequence: Long = 0,
    val savedAt: Long = 0,
)

@Entity(tableName = "book_preferences", foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class BookPreferences(
    @PrimaryKey val bookId: String,
    val mode: String = "PAGE",
    val direction: String = "LTR",
)

@Entity(tableName = "bookmarks", indices = [Index("bookId")], foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class Bookmark(
    @PrimaryKey val id: String,
    val bookId: String,
    val offset: Int,
    val chapter: String,
    val snippet: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "folders")
data class FolderSource(@PrimaryKey val uri: String, val name: String, val recursive: Boolean = true)

@Entity(tableName = "reading_times", primaryKeys = ["sessionId", "day"],
    foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("day")])
data class ReadingTimeRecord(val sessionId: String, val day: String, val bookId: String, val durationMs: Long)

data class ReadingTotals(val todayMs: Long = 0, val totalMs: Long = 0)
data class BookReadingTime(val bookId: String, val totalMs: Long)

data class LibraryBook(
    @Embedded val book: Book,
    @Relation(parentColumn = "id", entityColumn = "bookId") val position: ReadingPosition?,
)

@Dao
interface ViewerDao {
    @Transaction @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, importedAt DESC")
    fun observeLibrary(): Flow<List<LibraryBook>>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun book(id: String): Book?
    @Query("SELECT * FROM books WHERE sourceUri = :uri LIMIT 1") suspend fun byUri(uri: String): Book?
    @Upsert suspend fun putBook(book: Book)
    @Query("UPDATE books SET lastOpenedAt=:now, inHistory=1 WHERE id=:id") suspend fun opened(id: String, now: Long)
    @Query("UPDATE books SET onShelf=:visible WHERE id=:id") suspend fun shelf(id: String, visible: Boolean)
    @Query("UPDATE books SET inHistory=0") suspend fun clearHistory()
    @Query("UPDATE books SET sourceUri=:uri, storage='DIRECT', localPath=NULL WHERE id=:id") suspend fun relink(id: String, uri: String)
    @Query("UPDATE books SET encoding=:encoding WHERE id=:id") suspend fun encoding(id: String, encoding: String)
    @Query("SELECT * FROM positions WHERE bookId=:id") suspend fun position(id: String): ReadingPosition?
    @Upsert suspend fun putPosition(position: ReadingPosition)
    @Query("UPDATE positions SET textOffset=:textOffset, imageIndex=:imageIndex, imageEntry=:imageEntry, imageRatio=:imageRatio, progress=:progress, sequence=:sequence, savedAt=:now WHERE bookId=:id AND sessionId=:session AND sequence<=:sequence")
    suspend fun savePosition(id: String, session: String, textOffset: Int, imageIndex: Int, imageEntry: String, imageRatio: Float, progress: Float, sequence: Long, now: Long)
    @Query("SELECT * FROM book_preferences WHERE bookId=:id") suspend fun preferences(id: String): BookPreferences?
    @Upsert suspend fun putPreferences(preferences: BookPreferences)
    @Query("SELECT * FROM bookmarks WHERE bookId=:id ORDER BY offset") fun bookmarks(id: String): Flow<List<Bookmark>>
    @Upsert suspend fun putBookmark(bookmark: Bookmark)
    @Query("DELETE FROM bookmarks WHERE id=:id") suspend fun deleteBookmark(id: String)
    @Query("SELECT * FROM folders ORDER BY name") fun folders(): Flow<List<FolderSource>>
    @Upsert suspend fun putFolder(folder: FolderSource)
    @Query("DELETE FROM folders WHERE uri=:uri") suspend fun deleteFolder(uri: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTimeIfAbsent(record: ReadingTimeRecord)
    @Query("UPDATE reading_times SET durationMs=MAX(durationMs, :total) WHERE sessionId=:session AND day=:day AND bookId=:book")
    suspend fun keepMaximumTime(session: String, day: String, book: String, total: Long)
    @Transaction suspend fun saveReadingTimes(records: List<ReadingTimeRecord>) {
        records.filter { it.durationMs > 0 }.forEach { record ->
            insertTimeIfAbsent(record.copy(durationMs = 0))
            keepMaximumTime(record.sessionId, record.day, record.bookId, record.durationMs)
        }
    }
    @Query("SELECT COALESCE(SUM(CASE WHEN day=:today THEN durationMs ELSE 0 END),0) AS todayMs, COALESCE(SUM(durationMs),0) AS totalMs FROM reading_times")
    fun observeReadingTotals(today: String): Flow<ReadingTotals>
    @Query("SELECT bookId, SUM(durationMs) AS totalMs FROM reading_times GROUP BY bookId")
    fun observeBookReadingTimes(): Flow<List<BookReadingTime>>
}

@Database(entities = [Book::class, ReadingPosition::class, BookPreferences::class, Bookmark::class, FolderSource::class, ReadingTimeRecord::class], version = 2, exportSchema = true)
abstract class ViewerDatabase : RoomDatabase() {
    abstract fun dao(): ViewerDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reading_times` (`sessionId` TEXT NOT NULL, `day` TEXT NOT NULL, `bookId` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `day`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_times_bookId` ON `reading_times` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_times_day` ON `reading_times` (`day`)")
            }
        }
    }
}

