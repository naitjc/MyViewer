package com.chen.myviewer.source

import android.content.Context
import android.graphics.BitmapFactory
import com.chen.myviewer.data.Book
import com.chen.myviewer.data.BookPreferences
import com.chen.myviewer.data.ViewerDao
import com.chen.myviewer.reader.comic.ComicArchive
import com.dylibso.chicory.wasm.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream

const val COMMUNITY_SOURCE_URL = "https://aidoku-community.github.io/sources/index.min.json"

data class SourceEntry(val id: String, val name: String, val version: Int, val languages: List<String>, val downloadUrl: String, val baseUrl: String, val contentRating: Int) {
    fun json() = JSONObject().put("id", id).put("name", name).put("version", version).put("languages", JSONArray(languages))
        .put("downloadURL", downloadUrl).put("baseURL", baseUrl).put("contentRating", contentRating)
    companion object {
        fun parse(json: JSONObject, indexUrl: String) = SourceEntry(
            json.getString("id"), json.getString("name"), json.getInt("version"), json.optJSONArray("languages").strings(),
            URI(indexUrl).resolve(json.getString("downloadURL")).toString(), json.optString("baseURL"), json.optInt("contentRating"))
    }
}
data class InstalledSource(val entry: SourceEntry, val directory: String) {
    val id: String get() = entry.id
}
data class OnlineTitle(val book: Book, val sourceId: String, val manga: SourceManga)

class SourceRepository internal constructor(private val context: Context, private val dao: ViewerDao, private val http: SourceHttp = SourceHttp(context)) {
    private val root = File(context.filesDir, "sources")
    private val installLock = Mutex()
    private val titleLock = Mutex()
    private val downloadLock = java.util.concurrent.locks.ReentrantLock()
    private val cacheLock = Any()
    private val engines = object : LinkedHashMap<String, AidokuEngine>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AidokuEngine>?): Boolean = size > 2
    }
    private val preferences = context.getSharedPreferences("source-repositories", Context.MODE_PRIVATE)
    val repositoryUrl: String get() = preferences.getString("url", COMMUNITY_SOURCE_URL) ?: COMMUNITY_SOURCE_URL

    suspend fun catalog(refresh: Boolean = false, url: String = repositoryUrl): List<SourceEntry> = runInterruptible(Dispatchers.IO) {
        val normalized = normalizeIndexUrl(url)
        val cache = File(root, "catalog-${ComicArchive.digest(normalized)}.json")
        val data = if (!refresh && cache.exists()) cache.readBytes() else {
            val result = http.request(normalized)
            require(result.status in 200..299) { "源仓库加载失败（HTTP ${result.status}）。" }
            require(result.data.size <= 4 * 1024 * 1024) { "源仓库索引过大。" }
            result.data
        }
        val entries = parseCatalog(data.toString(Charsets.UTF_8), normalized)
        atomicWrite(cache, data)
        preferences.edit().putString("url", normalized).apply()
        entries
    }

    fun installed(): List<InstalledSource> = root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { directory ->
        runCatching {
            val record = JSONObject(File(directory, "installed.json").readText())
            val entry = SourceEntry.parse(record.getJSONObject("entry"), COMMUNITY_SOURCE_URL)
            val payload = File(directory, record.getString("payload"))
            require(File(payload, "main.wasm").isFile)
            InstalledSource(entry, payload.absolutePath)
        }.getOrNull()
    }.sortedBy { it.entry.name }

    suspend fun install(entry: SourceEntry): InstalledSource = installLock.withLock {
        runInterruptible(Dispatchers.IO) {
            require(entry.id.matches(Regex("[A-Za-z0-9._-]+")) && entry.id !in setOf(".", "..")) { "漫画源 ID 无效。" }
            val response = http.request(entry.downloadUrl)
            require(response.status in 200..299) { "源安装包下载失败（HTTP ${response.status}）。" }
            installPackage(entry, response.data)
        }
    }

    internal fun installPackage(entry: SourceEntry, bytes: ByteArray): InstalledSource {
        require(entry.id.matches(Regex("[A-Za-z0-9._-]+")) && entry.id !in setOf(".", "..")) { "漫画源 ID 无效。" }
        val directory = File(root, entry.id).apply { mkdirs() }
        val stage = File(directory, "pending-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            require(bytes.size <= 32 * 1024 * 1024) { "漫画源安装包过大。" }
            val allowed = setOf("main.wasm", "source.json", "settings.json", "filters.json", "icon.png")
            val extracted = mutableSetOf<String>()
            ZipInputStream(bytes.inputStream()).use { zip ->
                var item = zip.nextEntry
                var count = 0
                var totalSize = 0
                while (item != null) {
                    require(++count <= 32) { "漫画源安装包文件过多。" }
                    val name = item.name.removePrefix("Payload/")
                    require(!item.name.contains("..") && !item.name.startsWith('/')) { "漫画源安装包路径无效。" }
                    if (!item.isDirectory) {
                        val data = boundedBytes(zip, if (name == "main.wasm") 16 * 1024 * 1024 else 4 * 1024 * 1024)
                        totalSize += data.size
                        require(totalSize <= 32 * 1024 * 1024) { "漫画源安装包解压后过大。" }
                        if (name in allowed) {
                            require(extracted.add(name)) { "漫画源安装包含有重复文件。" }
                            File(stage, name).writeBytes(data)
                        }
                    }
                    zip.closeEntry()
                    item = zip.nextEntry
                }
            }
            val info = JSONObject(File(stage, "source.json").readText()).getJSONObject("info")
            require(info.getString("id") == entry.id && info.getInt("version") == entry.version) { "源安装包与索引信息不匹配。" }
            val module = Parser.parse(File(stage, "main.wasm"))
            val exports = (0 until module.exportSection().exportCount()).map { module.exportSection().getExport(it).name() }.toSet()
            require(exports.containsAll(setOf("get_search_manga_list", "get_manga_update", "get_page_list", "free_result"))) { "这个安装包使用的 Aidoku 源协议暂不兼容。" }
            val config = JSONObject(File(stage, "source.json").readText()).optJSONObject("config")
            require((config?.optInt("breakingChangeVersion") ?: 0) <= (installed().firstOrNull { it.id == entry.id }?.entry?.version ?: entry.version)) { "这个源更新改变了作品标识，需要迁移已有书库后再安装。" }
            val target = File(directory, "v${entry.version}-${java.util.UUID.randomUUID()}")
            check(stage.renameTo(target)) { "无法保存漫画源。" }
            atomicWrite(File(directory, "installed.json"), JSONObject().put("entry", entry.json()).put("payload", target.name).toString().toByteArray())
            synchronized(engines) { engines.keys.removeAll { it.startsWith("${entry.id}/") } }
            return InstalledSource(entry, target.absolutePath)
        } finally { stage.deleteRecursively() }
    }

    private fun engine(sourceId: String): AidokuEngine {
        val source = installed().firstOrNull { it.id == sourceId } ?: error("请先在发现页安装这个漫画源。")
        val key = "${source.id}/${source.directory}"
        return synchronized(engines) { engines.getOrPut(key) { AidokuEngine(context, source, http) } }
    }
    private fun <T> withEngine(sourceId: String, operation: (AidokuEngine) -> T): T {
        val engine = engine(sourceId)
        try {
            return operation(engine)
        } catch (error: Throwable) {
            // An interrupted host call can leave Rust's internal state borrowed.
            synchronized(engines) { engines.entries.removeAll { it.value === engine } }
            throw error
        }
    }
    suspend fun settings(sourceId: String): List<SourcePreference> = runInterruptible(Dispatchers.IO) {
        val source = installed().firstOrNull { it.id == sourceId } ?: error("请先安装漫画源。")
        SourcePreferences(context).read(source)
    }
    suspend fun setPreference(sourceId: String, setting: SourcePreference) = runInterruptible(Dispatchers.IO) {
        SourcePreferences(context).write(sourceId, setting)
        synchronized(engines) { engines.keys.removeAll { it.startsWith("$sourceId/") } }
    }
    suspend fun cover(url: String): android.graphics.Bitmap? = runInterruptible(Dispatchers.IO) {
        val file = File(context.cacheDir, "online-covers/${ComicArchive.digest(url)}.img")
        synchronized(file.absolutePath.intern()) { http.download(url, file) }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0) { file.delete(); return@runInterruptible null }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 480) options.inSampleSize *= 2
        trimCache(file)
        BitmapFactory.decodeFile(file.absolutePath, options)
    }
    suspend fun search(sourceId: String, query: String, page: Int) = runInterruptible(Dispatchers.IO) { withEngine(sourceId) { it.search(query, page) } }
    suspend fun details(sourceId: String, manga: SourceManga) = runInterruptible(Dispatchers.IO) { withEngine(sourceId) { it.details(manga) } }

    suspend fun saveTitle(sourceId: String, manga: SourceManga, onShelf: Boolean = true): OnlineTitle = titleLock.withLock { kotlinx.coroutines.withContext(Dispatchers.IO) {
        val id = "online-${ComicArchive.digest("$sourceId\u0000${manga.key}")}"
        val previous = dao.book(id)
        val metadata = File(context.filesDir, "online/$id.json")
        atomicWrite(metadata, JSONObject().put("sourceId", sourceId).put("manga", manga.json()).toString().toByteArray())
        val book = previous?.copy(title = manga.title, onShelf = previous.onShelf || onShelf)
            ?: Book(id, manga.title, "COMIC", "ONLINE", "myviewer://online/$id", metadata.absolutePath, onShelf = onShelf)
        if (previous == null) dao.putBook(book) else dao.updateOnlineTitle(id, manga.title, onShelf)
        if (previous == null) dao.putPreferences(BookPreferences(id, if (manga.viewer in setOf(3, 4)) "SCROLL" else "PAGE", if (manga.viewer == 2) "RTL" else "LTR"))
        OnlineTitle(dao.book(id) ?: book, sourceId, manga)
    } }
    suspend fun title(book: Book): OnlineTitle = runInterruptible(Dispatchers.IO) {
        val metadata = JSONObject(File(requireNotNull(book.localPath)).readText())
        OnlineTitle(book, metadata.getString("sourceId"), SourceManga.fromJson(metadata.getJSONObject("manga")))
    }
    internal suspend fun chapterPages(title: OnlineTitle, chapter: SourceChapter): List<SourcePage> = runInterruptible(Dispatchers.IO) {
        val manifest = File(chapterDirectory(title.book.id, chapter.key), "pages.json")
        if (manifest.exists()) return@runInterruptible readPages(manifest)
        val pages = withEngine(title.sourceId) { it.pages(title.manga, chapter) }
        require(pages.size <= 10_000) { "章节页数过多。" }
        pages
    }
    internal fun chapterDirectory(bookId: String, chapterKey: String) = File(context.filesDir, "downloads/$bookId/${ComicArchive.digest(chapterKey)}")
    internal fun cacheDirectory(bookId: String, chapterKey: String) = File(context.cacheDir, "online/$bookId/${ComicArchive.digest(chapterKey)}")
    internal fun imageFile(title: OnlineTitle, chapter: SourceChapter, pages: List<SourcePage>, index: Int): File {
        val saved = File(chapterDirectory(title.book.id, chapter.key), "$index.img")
        if (File(saved.parentFile, "pages.json").exists() && saved.exists()) return saved
        val page = pages[index]
        val target = File(cacheDirectory(title.book.id, chapter.key), "$index-${ComicArchive.digest(page.url)}.img")
        if (target.exists() && target.length() > 0) { target.setLastModified(System.currentTimeMillis()); return target }
        val request = withEngine(title.sourceId) { it.imageRequest(page) }
        require(request.method == "GET") { "这个源的图片请求方式尚未支持。" }
        synchronized(target.absolutePath.intern()) { http.download(request.url, target, request.headers) }
        trimCache(target)
        return target
    }
    suspend fun download(title: OnlineTitle, chapter: SourceChapter, progress: (Int, Int) -> Unit) = runInterruptible(Dispatchers.IO) {
        downloadLock.lockInterruptibly()
        try {
            val destination = chapterDirectory(title.book.id, chapter.key).apply { mkdirs() }
            val manifest = File(destination, "pages.json")
            if (manifest.exists()) {
                val count = readPages(manifest).size
                progress(count, count)
                return@runInterruptible
            }
            val pages = withEngine(title.sourceId) { it.pages(title.manga, chapter) }
            require(pages.isNotEmpty() && pages.size <= 10_000) { "章节页数无效。" }
            val pendingManifest = File(destination, "pending-pages.json")
            val previous = runCatching { readPages(pendingManifest) }.getOrNull()
            if (previous != pages) {
                destination.listFiles().orEmpty().filter { it.extension == "img" }.forEach { it.delete() }
            }
            val encoded = JSONArray(pages.map { page ->
                JSONObject().put("url", page.url).put("context", page.context?.let { JSONObject(it) })
            }).toString().toByteArray()
            atomicWrite(pendingManifest, encoded)
            pages.forEachIndexed { index, _ ->
                val file = File(destination, "$index.img")
                if (!file.exists() || file.length() == 0L) {
                    atomicWrite(file, imageFile(title, chapter, pages, index).readBytes())
                }
                progress(index + 1, pages.size)
            }
            atomicWrite(manifest, encoded)
            pendingManifest.delete()
        } finally {
            downloadLock.unlock()
        }
    }
    fun downloaded(bookId: String, chapterKey: String) = File(chapterDirectory(bookId, chapterKey), "pages.json").exists()
    suspend fun deleteDownload(bookId: String, chapterKey: String) = runInterruptible(Dispatchers.IO) {
        downloadLock.lockInterruptibly()
        try { check(chapterDirectory(bookId, chapterKey).deleteRecursively()) { "删除下载失败，请重试。" } }
        finally { downloadLock.unlock() }
    }
    private fun trimCache(keep: File) = synchronized(cacheLock) {
        val files = listOf("online", "online-covers").flatMap { name ->
            File(context.cacheDir, name).walkTopDown().filter { it.isFile && it.extension == "img" }.toList()
        }
        var total = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total > 256L * 1024 * 1024 && file != keep) {
                val length = file.length()
                if (file.delete()) total -= length
            }
        }
    }
    private fun readPages(file: File): List<SourcePage> {
        val array = JSONArray(file.readText())
        return List(array.length()) { index ->
            val value = array.getJSONObject(index)
            val context = value.optJSONObject("context")?.let { json -> json.keys().asSequence().associateWith { json.getString(it) } }
            SourcePage(value.getString("url"), context)
        }
    }
    companion object {
        fun normalizeIndexUrl(url: String): String {
            val uri = URI(url.trim())
            require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "请输入 HTTPS 漫画源仓库地址。" }
            return if (uri.path.endsWith(".json")) uri.toString() else uri.toString().trimEnd('/') + "/index.min.json"
        }
        internal fun parseCatalog(json: String, url: String): List<SourceEntry> {
            val array = JSONObject(json).getJSONArray("sources")
            require(array.length() <= 10_000)
            return List(array.length()) { SourceEntry.parse(array.getJSONObject(it), url) }.distinctBy { it.id }
        }
    }
}
