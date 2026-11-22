package com.chen.myviewer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupSummary(val books: Int, val bookmarks: Int, val readingTimes: Int)

object BackupManager {
    private const val BACKUP_VERSION = 1

    suspend fun exportJson(dao: ViewerDao, settingsStore: SettingsStore): String {
        val books = dao.allBooks()
        val positions = dao.allPositions()
        val chapters = dao.allChapterPositions()
        val prefs = dao.allBookPreferences()
        val bookmarks = dao.allBookmarks()
        val times = dao.allReadingTimes()
        val reader = settingsStore.settings.first()
        val sort = settingsStore.librarySort.first()
        val columns = settingsStore.libraryColumns.first()

        return JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("books", JSONArray(books.map { book ->
                JSONObject().apply {
                    put("id", book.id)
                    put("title", book.title)
                    put("kind", book.kind)
                    put("storage", book.storage)
                    put("sourceUri", book.sourceUri)
                    put("localPath", book.localPath ?: JSONObject.NULL)
                    put("coverPath", book.coverPath ?: JSONObject.NULL)
                    put("size", book.size)
                    put("importedAt", book.importedAt)
                    put("lastOpenedAt", book.lastOpenedAt)
                    put("onShelf", book.onShelf)
                    put("inHistory", book.inHistory)
                    put("encoding", book.encoding)
                    put("completed", book.completed)
                    put("pinned", book.pinned)
                    put("unreadChapters", book.unreadChapters)
                }
            }))
            put("positions", JSONArray(positions.map { pos ->
                JSONObject().apply {
                    put("bookId", pos.bookId)
                    put("textOffset", pos.textOffset)
                    put("imageIndex", pos.imageIndex)
                    put("imageEntry", pos.imageEntry)
                    put("imageRatio", pos.imageRatio.toDouble())
                    put("progress", pos.progress.toDouble())
                    put("sessionId", pos.sessionId)
                    put("sequence", pos.sequence)
                    put("savedAt", pos.savedAt)
                    put("chapterKey", pos.chapterKey)
                }
            }))
            put("chapterPositions", JSONArray(chapters.map { ch ->
                JSONObject().apply {
                    put("bookId", ch.bookId)
                    put("chapterKey", ch.chapterKey)
                    put("imageIndex", ch.imageIndex)
                    put("imageRatio", ch.imageRatio.toDouble())
                    put("progress", ch.progress.toDouble())
                    put("completed", ch.completed ?: JSONObject.NULL)
                    put("textOffset", ch.textOffset)
                }
            }))
            put("bookPreferences", JSONArray(prefs.map { pref ->
                JSONObject().apply {
                    put("bookId", pref.bookId)
                    put("mode", pref.mode)
                    put("direction", pref.direction)
                }
            }))
            put("bookmarks", JSONArray(bookmarks.map { mark ->
                JSONObject().apply {
                    put("id", mark.id)
                    put("bookId", mark.bookId)
                    put("offset", mark.offset)
                    put("chapter", mark.chapter)
                    put("snippet", mark.snippet)
                    put("note", mark.note)
                    put("createdAt", mark.createdAt)
                    put("chapterKey", mark.chapterKey)
                }
            }))
            put("readingTimes", JSONArray(times.map { time ->
                JSONObject().apply {
                    put("sessionId", time.sessionId)
                    put("day", time.day)
                    put("bookId", time.bookId)
                    put("durationMs", time.durationMs)
                }
            }))
            put("settings", JSONObject().apply {
                put("fontSize", reader.fontSize.toDouble())
                put("lineSpacing", reader.lineSpacing.toDouble())
                put("background", reader.background)
                put("brightness", reader.brightness.toDouble())
                put("animation", reader.animation)
                put("copyOnImport", reader.copyOnImport)
                put("autoPageSeconds", reader.autoPageSeconds)
                put("autoScrollSpeed", reader.autoScrollSpeed)
                put("immersiveMode", reader.immersiveMode)
                put("showFooterBar", reader.showFooterBar)
                put("librarySort", sort.name)
                put("libraryColumns", columns)
            })
        }.toString()
    }

    suspend fun importJson(json: String, dao: ViewerDao, settingsStore: SettingsStore): BackupSummary {
        val root = JSONObject(json)
        require(root.optInt("version", -1) == BACKUP_VERSION) { "不支持的备份文件版本。" }

        val booksArray = root.optJSONArray("books") ?: JSONArray()
        val books = (0 until booksArray.length()).map { i ->
            val obj = booksArray.getJSONObject(i)
            Book(
                id = obj.getString("id"),
                title = obj.getString("title"),
                kind = obj.getString("kind"),
                storage = obj.getString("storage"),
                sourceUri = obj.getString("sourceUri"),
                localPath = obj.optNullableString("localPath"),
                coverPath = obj.optNullableString("coverPath"),
                size = obj.optLong("size", 0L),
                importedAt = obj.optLong("importedAt", System.currentTimeMillis()),
                lastOpenedAt = obj.optLong("lastOpenedAt", 0L),
                onShelf = obj.optBoolean("onShelf", true),
                inHistory = obj.optBoolean("inHistory", false),
                encoding = obj.optString("encoding", "AUTO"),
                completed = obj.optBoolean("completed", false),
                pinned = obj.optBoolean("pinned", false),
                unreadChapters = obj.optInt("unreadChapters", 0),
            )
        }
        val bookIds = books.map { it.id }.toSet()

        val positionsArray = root.optJSONArray("positions") ?: JSONArray()
        val positions = (0 until positionsArray.length()).mapNotNull { i ->
            val obj = positionsArray.getJSONObject(i)
            val bookId = obj.getString("bookId")
            if (bookId !in bookIds) return@mapNotNull null
            ReadingPosition(
                bookId = bookId,
                textOffset = obj.optInt("textOffset", 0),
                imageIndex = obj.optInt("imageIndex", 0),
                imageEntry = obj.optString("imageEntry", ""),
                imageRatio = obj.optDouble("imageRatio", 0.0).toFloat(),
                progress = obj.optDouble("progress", 0.0).toFloat(),
                sessionId = obj.optString("sessionId", ""),
                sequence = obj.optLong("sequence", 0L),
                savedAt = obj.optLong("savedAt", 0L),
                chapterKey = obj.optString("chapterKey", ""),
            )
        }

        val chaptersArray = root.optJSONArray("chapterPositions") ?: JSONArray()
        val chapters = (0 until chaptersArray.length()).mapNotNull { i ->
            val obj = chaptersArray.getJSONObject(i)
            val bookId = obj.getString("bookId")
            if (bookId !in bookIds) return@mapNotNull null
            ChapterPosition(
                bookId = bookId,
                chapterKey = obj.getString("chapterKey"),
                imageIndex = obj.optInt("imageIndex", 0),
                imageRatio = obj.optDouble("imageRatio", 0.0).toFloat(),
                progress = obj.optDouble("progress", 0.0).toFloat(),
                completed = if (obj.isNull("completed")) null else obj.optBoolean("completed"),
                textOffset = obj.optInt("textOffset", 0),
            )
        }

        val prefsArray = root.optJSONArray("bookPreferences") ?: JSONArray()
        val prefs = (0 until prefsArray.length()).mapNotNull { i ->
            val obj = prefsArray.getJSONObject(i)
            val bookId = obj.getString("bookId")
            if (bookId !in bookIds) return@mapNotNull null
            BookPreferences(
                bookId = bookId,
                mode = obj.optString("mode", "PAGE"),
                direction = obj.optString("direction", "LTR"),
            )
        }

        val bookmarksArray = root.optJSONArray("bookmarks") ?: JSONArray()
        val bookmarks = (0 until bookmarksArray.length()).mapNotNull { i ->
            val obj = bookmarksArray.getJSONObject(i)
            val bookId = obj.getString("bookId")
            if (bookId !in bookIds) return@mapNotNull null
            Bookmark(
                id = obj.getString("id"),
                bookId = bookId,
                offset = obj.optInt("offset", 0),
                chapter = obj.optString("chapter", ""),
                snippet = obj.optString("snippet", ""),
                note = obj.optString("note", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                chapterKey = obj.optString("chapterKey", ""),
            )
        }

        val timesArray = root.optJSONArray("readingTimes") ?: JSONArray()
        val times = (0 until timesArray.length()).mapNotNull { i ->
            val obj = timesArray.getJSONObject(i)
            val bookId = obj.getString("bookId")
            if (bookId !in bookIds) return@mapNotNull null
            ReadingTimeRecord(
                sessionId = obj.getString("sessionId"),
                day = obj.getString("day"),
                bookId = bookId,
                durationMs = obj.optLong("durationMs", 0L),
            )
        }

        dao.restoreBackup(books, positions, chapters, prefs, bookmarks, times)

        root.optJSONObject("settings")?.let { s ->
            val current = settingsStore.settings.first()
            settingsStore.update(
                current.copy(
                    fontSize = s.optDouble("fontSize", current.fontSize.toDouble()).toFloat().coerceIn(14f, 36f),
                    lineSpacing = s.optDouble("lineSpacing", current.lineSpacing.toDouble()).toFloat().coerceIn(1.2f, 2.3f),
                    background = s.optString("background", current.background),
                    brightness = s.optDouble("brightness", current.brightness.toDouble()).toFloat().coerceIn(-1f, 1f),
                    animation = s.optString("animation", current.animation),
                    copyOnImport = s.optBoolean("copyOnImport", current.copyOnImport),
                    autoPageSeconds = s.optInt("autoPageSeconds", current.autoPageSeconds).coerceIn(2, 60),
                    autoScrollSpeed = s.optInt("autoScrollSpeed", current.autoScrollSpeed).coerceIn(1, 10),
                    immersiveMode = s.optBoolean("immersiveMode", current.immersiveMode),
                    showFooterBar = s.optBoolean("showFooterBar", current.showFooterBar),
                )
            )
            LibrarySort.entries.firstOrNull { it.name == s.optString("librarySort") }?.let { settingsStore.setLibrarySort(it) }
            if (s.has("libraryColumns")) settingsStore.setLibraryColumns(s.optInt("libraryColumns", 3))
        }

        return BackupSummary(books.size, bookmarks.size, times.size)
    }

    suspend fun exportToUri(context: Context, uri: Uri, dao: ViewerDao, settingsStore: SettingsStore): BackupSummary {
        val json = exportJson(dao, settingsStore)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入备份文件。")
        }
        val root = JSONObject(json)
        return BackupSummary(
            books = root.optJSONArray("books")?.length() ?: 0,
            bookmarks = root.optJSONArray("bookmarks")?.length() ?: 0,
            readingTimes = root.optJSONArray("readingTimes")?.length() ?: 0,
        )
    }

    suspend fun importFromUri(context: Context, uri: Uri, dao: ViewerDao, settingsStore: SettingsStore): BackupSummary {
        val json = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("无法读取备份文件。")
        }
        return importJson(json, dao, settingsStore)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
