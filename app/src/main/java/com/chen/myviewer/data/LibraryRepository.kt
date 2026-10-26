package com.chen.myviewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.chen.myviewer.reader.comic.ComicArchive
import com.chen.myviewer.reader.text.TextDocument
import com.chen.myviewer.reader.text.TextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class ImportCandidate(val uri: Uri, val name: String, val size: Long, val directAllowed: Boolean = true)
data class ImportResult(val added: Int, val existing: Int, val failures: List<String>)

class LibraryRepository(private val context: Context, val dao: ViewerDao) {
    private val importLock = Mutex()
    suspend fun describe(uri: Uri): ImportCandidate = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "未命名文件"; var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) { if (!it.isNull(0)) name = it.getString(0); if (!it.isNull(1)) size = it.getLong(1) }
        }
        ImportCandidate(uri, name, size)
    }
    suspend fun scan(uri: Uri, recursive: Boolean, onCount: (Int) -> Unit = {}): List<ImportCandidate> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: error("无法读取文件夹，请重新授权。")
        require(root.isDirectory && root.canRead()) { "这个文件夹无法读取，请重新选择并授权。" }
        val pending = ArrayDeque<DocumentFile>(); pending.add(root)
        val seen = mutableSetOf<String>(); val found = mutableListOf<ImportCandidate>()
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val directory = pending.removeFirst()
            if (!seen.add(directory.uri.toString())) continue
            directory.listFiles().forEach { file ->
                coroutineContext.ensureActive()
                if (file.isDirectory && recursive) pending.add(file)
                if (file.isFile && supported(file.name.orEmpty())) {
                    found += ImportCandidate(file.uri, file.name ?: "未命名", file.length()); onCount(found.size)
                }
            }
        }
        dao.putFolder(FolderSource(uri.toString(), root.name ?: "文件夹", recursive))
        found.sortedWith { a, b -> com.chen.myviewer.reader.comic.NaturalOrder.compare(a.name, b.name) }
    }
    suspend fun import(candidates: List<ImportCandidate>, copy: Boolean, onProgress: (Int, Int) -> Unit): ImportResult = importLock.withLock {
        withContext(Dispatchers.IO) {
            var added = 0; var existing = 0; val failures = mutableListOf<String>()
            candidates.distinctBy { it.uri }.forEachIndexed { index, candidate ->
                coroutineContext.ensureActive()
                var owned: File? = null
                try {
                    require(supported(candidate.name)) { "仅支持 TXT 和 ZIP。" }
                    require(copy || candidate.directAllowed) { "这个来源未提供长期读取授权，请选择复制导入。" }
                    val previous = dao.byUri(candidate.uri.toString())
                    if (previous != null) { dao.shelf(previous.id, true); existing++ }
                    else {
                        val id = UUID.randomUUID().toString()
                        val kind = if (candidate.name.endsWith(".txt", true)) "TEXT" else "COMIC"
                        if (copy) {
                            val directory = File(context.filesDir, "books/$id").apply { mkdirs() }
                            val pending = File(directory, "source.part")
                            try {
                                context.contentResolver.openInputStream(candidate.uri)?.use { input -> pending.outputStream().use { output ->
                                    val buffer = ByteArray(128 * 1024)
                                    while (true) { coroutineContext.ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                                } } ?: error("原文件无法读取。")
                                require(pending.length() > 0) { "文件为空。" }
                                if (candidate.size > 0) require(pending.length() == candidate.size) { "复制未完成，请重试。" }
                                owned = File(directory, if (kind == "TEXT") "source.txt" else "source.zip")
                                check(pending.renameTo(owned)) { "无法保存文件副本。" }
                            } finally { pending.delete() }
                        }
                        var book = Book(id, candidate.name.substringBeforeLast('.'), kind, if (copy) "COPY" else "DIRECT", candidate.uri.toString(), owned?.absolutePath, size = candidate.size)
                        if (kind == "COMIC") {
                            ComicArchive.open(context, book).use { archive ->
                                val file = archive.imageFile(0)
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(file.absolutePath, options)
                                options.inJustDecodeBounds = false; options.inSampleSize = 1
                                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 640) options.inSampleSize *= 2
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                                if (bitmap != null) {
                                    val cover = File(context.filesDir, "covers/$id.jpg").apply { parentFile?.mkdirs() }
                                    cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }; bitmap.recycle()
                                    book = book.copy(coverPath = cover.absolutePath)
                                }
                            }
                        } else {
                            openStream(book).use { require(it.read() >= 0) { "TXT 文件为空。" } }
                        }
                        dao.putBook(book); added++
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { owned?.parentFile?.deleteRecursively(); throw e }
                catch (e: Exception) { owned?.parentFile?.deleteRecursively(); failures += "${candidate.name}：${e.message ?: "导入失败"}" }
                onProgress(index + 1, candidates.size)
            }
            ImportResult(added, existing, failures)
        }
    }
    fun openStream(book: Book): InputStream = if (book.storage == "COPY") File(requireNotNull(book.localPath)).inputStream()
        else context.contentResolver.openInputStream(Uri.parse(book.sourceUri)) ?: error("原文件无法打开，请重新定位。")
    suspend fun loadText(book: Book): TextDocument = withContext(Dispatchers.IO) { TextParser.parseSource({ openStream(book) }, book.encoding) }
    suspend fun loadComic(book: Book): ComicArchive = withContext(Dispatchers.IO) { ComicArchive.open(context, book) }
    suspend fun startSession(book: Book): ReadingPosition = withContext(Dispatchers.IO) {
        val state = (dao.position(book.id) ?: ReadingPosition(book.id)).copy(sessionId = UUID.randomUUID().toString(), sequence = 0)
        dao.putPosition(state); dao.opened(book.id, System.currentTimeMillis()); state
    }
    suspend fun save(position: ReadingPosition) { dao.savePosition(position.bookId, position.sessionId, position.textOffset, position.imageIndex, position.imageEntry, position.imageRatio, position.progress.coerceIn(0f, 1f), position.sequence, System.currentTimeMillis()) }
    companion object { fun supported(name: String) = name.endsWith(".txt", true) || name.endsWith(".zip", true) }
}
