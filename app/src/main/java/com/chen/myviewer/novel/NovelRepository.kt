package com.chen.myviewer.novel

import android.content.Context
import androidx.core.content.edit
import com.chen.myviewer.data.Book
import com.chen.myviewer.data.ViewerDao
import com.chen.myviewer.reader.comic.ComicArchive
import com.chen.myviewer.reader.text.TextDocument
import com.chen.myviewer.reader.text.Chapter
import com.chen.myviewer.source.SourceHttp
import com.chen.myviewer.source.atomicWrite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NovelRepository internal constructor(private val context: Context, private val dao: ViewerDao, private val http: SourceHttp) {
    constructor(context: Context, dao: ViewerDao) : this(context, dao, SourceHttp(context))
    private val root = File(context.filesDir, "novels")
    private val sourceFile = File(root, "sources.json")
    private val lock = Mutex()
    private val preferences = context.getSharedPreferences("novel-catalogs", Context.MODE_PRIVATE)
    val catalogFilter: String? get() = preferences.getString("filter", null)
    fun setCatalogFilter(url: String?) { preferences.edit { putString("filter", url) } }

    fun sources(): List<NovelSource> = synchronized(sourceFile.absolutePath.intern()) {
        if (!sourceFile.exists()) emptyList() else parseSources(sourceFile.readText())
    }
    suspend fun importUrl(url: String): Int {
        require(url.startsWith("https://") || url.startsWith("http://")) { "请输入有效的书源 JSON 地址。" }
        val response = runInterruptible(Dispatchers.IO) { http.request(url) }
        require(response.status in 200..299) { "书源下载失败（HTTP ${response.status}）。" }
        return importJson(response.data.toString(Charsets.UTF_8), url)
    }
    suspend fun importJson(json: String, catalog: String? = null): Int = lock.withLock {
        runInterruptible(Dispatchers.IO) {
            val imported = parseSources(json)
            require(imported.isNotEmpty()) { "文件中没有 Legado 小说书源。" }
            synchronized(sourceFile.absolutePath.intern()) {
                val existing = sources().associateBy { it.id }
                imported.forEach { source ->
                    val origin = catalog ?: existing[source.id]?.catalog
                    if (origin != null) source.json.put("_myviewerCatalog", origin)
                }
                val merged = (existing.values + imported).associateBy { it.id }.values
                val payload = JSONArray(merged.map { it.json }).toString()
                require(merged.size <= 10_000 && payload.length <= 32 * 1024 * 1024) { "已保存的小说源超过容量上限。" }
                atomicWrite(sourceFile, payload.toByteArray())
            }
            imported.size
        }
    }
    suspend fun uninstall(id: String) = lock.withLock {
        runInterruptible(Dispatchers.IO) {
            synchronized(sourceFile.absolutePath.intern()) {
                atomicWrite(sourceFile, JSONArray(sources().filterNot { it.id == id }.map { it.json }).toString().toByteArray())
            }
        }
    }
    private fun source(id: String) = sources().firstOrNull { it.id == id } ?: error("请先导入这个小说源。")
    suspend fun search(id: String, key: String, page: Int): List<NovelBook> = runInterruptible(Dispatchers.IO) { LegadoEngine(source(id), http).search(key, page) }
    suspend fun details(id: String, book: NovelBook): NovelBook = runInterruptible(Dispatchers.IO) { LegadoEngine(source(id), http).details(book) }
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
                val content = LegadoEngine(source(title.sourceId), http).content(title.novel, chapter)
                atomicWrite(file, content.toByteArray())
            }
            val text = chapter.name + "\n\n" + file.readText()
            file.setLastModified(System.currentTimeMillis())
            if (file == cache) trimCache(cache)
            TextDocument(text, listOf(Chapter(chapter.name, 0)), "UTF-8")
        }
    }
    private fun trimCache(keep: File) {
        val files = File(context.cacheDir, "novel-chapters").walkTopDown().filter { it.isFile && it.extension == "txt" }.toList()
        var size = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (size <= 64L * 1024 * 1024) break
            if (file == keep) continue
            val bytes = file.length()
            if (file.delete()) size -= bytes
        }
    }
    fun downloaded(title: NovelTitle): Set<String> = title.novel.chapters.filter { downloadedFile(title.book.id, it.url).exists() }.map { it.url }.toSet()
    suspend fun download(title: NovelTitle, chapters: List<NovelChapter>, progress: (Int, Int) -> Unit) {
        for ((index, chapter) in chapters.withIndex()) {
            runInterruptible(Dispatchers.IO) {
                val target = downloadedFile(title.book.id, chapter.url)
                synchronized(target.absolutePath.intern()) {
                    if (!target.exists()) {
                        val cache = chapterFile(title.book.id, chapter.url)
                        val bytes = if (cache.exists()) cache.readBytes() else LegadoEngine(source(title.sourceId), http).content(title.novel, chapter).toByteArray()
                        atomicWrite(target, bytes)
                    }
                }
            }
            progress(index + 1, chapters.size)
        }
        dao.shelf(title.book.id, true)
    }
    suspend fun deleteDownloads(title: NovelTitle) = runInterruptible(Dispatchers.IO) {
        check(File(directory(title.book.id), "downloads").deleteRecursively()) { "删除离线正文失败。" }
    }
    suspend fun cover(title: NovelTitle) {
        if (title.novel.cover.isBlank()) return
        val file = File(directory(title.book.id), "cover.img")
        runInterruptible(Dispatchers.IO) { http.download(title.novel.cover, file) }
        dao.cover(title.book.id, file.absolutePath)
    }
    companion object {
        fun parseSources(json: String): List<NovelSource> {
            require(json.length <= 32 * 1024 * 1024) { "书源文件超过 32 MB。" }
            val value = json.trim().removePrefix("\uFEFF")
            val list = if (value.startsWith('[')) JSONArray(value) else JSONArray().put(JSONObject(value))
            require(list.length() <= 10_000) { "书源数量超过支持上限。" }
            return List(list.length()) { list.getJSONObject(it) }.filter { it.optInt("bookSourceType", 0) == 0 }.map { item ->
                require(item.optString("bookSourceName").isNotBlank() && item.optString("bookSourceUrl").isNotBlank()) { "小说书源缺少名称或地址。" }
                NovelSource(item)
            }.distinctBy { it.id }
        }
    }
}
