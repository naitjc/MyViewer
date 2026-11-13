package com.chen.myviewer.novel

import android.content.Context
import com.chen.myviewer.data.Book
import com.chen.myviewer.data.ViewerDao
import com.chen.myviewer.reader.comic.ComicArchive
import com.chen.myviewer.reader.text.TextDocument
import com.chen.myviewer.reader.text.Chapter
import com.chen.myviewer.source.atomicWrite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Reads chapters saved by previous versions without contacting a source. */
class NovelRepository(private val context: Context, private val dao: ViewerDao) {
    private val root = File(context.filesDir, "novels")
    private val lock = Mutex()
    private fun directory(bookId: String) = File(root, "books/$bookId")
    internal fun chapterFile(bookId: String, chapterUrl: String) = File(context.cacheDir, "novel-chapters/$bookId/${ComicArchive.digest(chapterUrl)}.txt")
    private fun downloadedFile(bookId: String, chapterUrl: String) = File(directory(bookId), "downloads/${ComicArchive.digest(chapterUrl)}.txt")
    suspend fun saveTitle(sourceId: String, novel: NovelBook, onShelf: Boolean = false): NovelTitle = lock.withLock {
        runInterruptible(Dispatchers.IO) {
            val id = "novel-${ComicArchive.digest("$sourceId\u0000${novel.url}")}"
            val file = File(directory(id), "book.json")
            atomicWrite(file, JSONObject().put("sourceId", sourceId).put("novel", novel.json()).toString().toByteArray())
            id
        }.let { id ->
            val previous = dao.book(id)
            if (previous == null) dao.putBook(Book(id, novel.name, "TEXT", "NOVEL", "myviewer://novel/$id", onShelf = onShelf))
            else dao.updateOnlineTitle(id, novel.name, onShelf)
            NovelTitle(dao.book(id)!!, sourceId, novel)
        }
    }
    suspend fun title(book: Book): NovelTitle = runInterruptible(Dispatchers.IO) {
        val json = JSONObject(File(directory(book.id), "book.json").readText())
        NovelTitle(book, json.getString("sourceId"), NovelBook.fromJson(json.getJSONObject("novel")))
    }
    suspend fun loadChapter(title: NovelTitle, chapter: NovelChapter): TextDocument = runInterruptible(Dispatchers.IO) {
        val permanent = downloadedFile(title.book.id, chapter.url)
        val cache = chapterFile(title.book.id, chapter.url)
        val file = if (permanent.exists()) permanent else cache
        synchronized(file.absolutePath.intern()) {
            if (!file.exists()) {
                error("该章节没有本地正文，请导入 TXT 文件阅读。")
            }
            val text = chapter.name + "\n\n" + file.readText()
            file.setLastModified(System.currentTimeMillis())
            TextDocument(text, listOf(Chapter(chapter.name, 0)), "UTF-8")
        }
    }

    fun available(title: NovelTitle): Set<String> = title.novel.chapters.filter {
        downloadedFile(title.book.id, it.url).exists() || chapterFile(title.book.id, it.url).exists()
    }.map { it.url }.toSet()
}
