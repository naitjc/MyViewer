package com.chen.myviewer.download

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.chen.myviewer.novel.NovelChapter
import com.chen.myviewer.novel.NovelRepository
import com.chen.myviewer.novel.NovelTitle
import com.chen.myviewer.source.OnlineTitle
import com.chen.myviewer.source.SourceChapter
import com.chen.myviewer.source.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/** Stages a complete file before writing it through the system folder grant. */
internal class FolderDownloadRepository(
    private val context: Context,
    private val sources: SourceRepository,
    private val novels: NovelRepository,
) {
    private val lock = Mutex()
    private val staging = File(context.cacheDir, "folder-downloads")

    suspend fun comic(title: OnlineTitle, chapters: List<SourceChapter>, tree: Uri, progress: (String, Int) -> Unit) = lock.withLock {
        require(chapters.isNotEmpty() && chapters.none { it.locked }) { "请选择可读取的章节。" }
        withContext(Dispatchers.IO) {
            val folder = destination(tree)
            prepareStaging()
            val temporary = File.createTempFile("comic-", ".zip", staging)
            try {
                ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                    if (title.manga.viewer == 2) {
                        zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                        zip.write("<ComicInfo><Manga>YesAndRightToLeft</Manga></ComicInfo>".toByteArray())
                        zip.closeEntry()
                    }
                    chapters.forEachIndexed { index, chapter ->
                        coroutineContext.ensureActive()
                        val pages = sources.chapterPages(title, chapter)
                        require(pages.isNotEmpty()) { "${chapter.label} 没有可下载的图片。" }
                        val directory = if (chapters.size > 1) "${(index + 1).toString().padStart(6, '0')} ${safeName(chapter.label)}/" else ""
                        runInterruptible {
                            pages.indices.forEach { page ->
                                progress("${title.manga.title} · ${index + 1}/${chapters.size} 章 · ${page + 1}/${pages.size} 页", 0)
                                val image = sources.imageFile(title, chapter, pages, page)
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(image.path, options)
                                val extension = when (options.outMimeType) {
                                    "image/jpeg" -> "jpg"
                                    "image/png" -> "png"
                                    "image/webp" -> "webp"
                                    "image/gif" -> "gif"
                                    else -> error("无法识别第 ${page + 1} 页的图片格式。")
                                }
                                zip.putNextEntry(ZipEntry("$directory${(page + 1).toString().padStart(5, '0')}.$extension"))
                                image.inputStream().use { copy(it, zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
                coroutineContext.ensureActive()
                val name = if (chapters.size == 1) "${title.manga.title.take(30).trimEnd { it.isHighSurrogate() }} - ${chapters.single().label}" else title.manga.title
                runInterruptible {
                    progress("正在保存 ${title.manga.title}.zip", 0)
                    publish(folder, temporary, name, "zip", "application/zip")
                    progress("已保存 ZIP，共 ${chapters.size} 章", 1)
                }
            } finally { temporary.delete() }
        }
    }

    suspend fun novel(title: NovelTitle, chapters: List<NovelChapter>, tree: Uri, progress: (String, Int) -> Unit) = lock.withLock {
        require(chapters.isNotEmpty() && chapters.none { it.locked }) { "请选择可读取的章节。" }
        withContext(Dispatchers.IO) {
            val folder = destination(tree)
            prepareStaging()
            val temporary = File.createTempFile("novel-", ".txt", staging)
            try {
                temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.appendLine(title.novel.name)
                    if (title.novel.author.isNotBlank()) writer.appendLine(title.novel.author)
                    writer.appendLine()
                    chapters.forEachIndexed { index, chapter ->
                        coroutineContext.ensureActive()
                        progress("${title.novel.name} · ${index + 1}/${chapters.size} 章 · ${chapter.name}", 0)
                        writer.append(novels.loadChapter(title, chapter).text)
                        writer.append("\n\n")
                    }
                }
                coroutineContext.ensureActive()
                val name = if (chapters.size == 1) "${title.novel.name} - ${chapters.single().name}" else title.novel.name
                runInterruptible {
                    progress("正在保存 TXT", 0)
                    publish(folder, temporary, name, "txt", "text/plain")
                    progress("已保存 TXT，共 ${chapters.size} 章", 1)
                }
            } finally { temporary.delete() }
        }
    }

    private fun destination(tree: Uri): DocumentFile {
        val folder = DocumentFile.fromTreeUri(context, tree)
        require(folder != null && folder.isDirectory && folder.canWrite()) { "无法写入这个文件夹，请重新选择并允许访问。" }
        return folder
    }

    private fun prepareStaging() {
        staging.mkdirs()
        // The repository lock also protects cleanup of files left by process termination.
        staging.listFiles().orEmpty().forEach { it.delete() }
    }

    internal fun publish(folder: DocumentFile, temporary: File, name: String, extension: String, mime: String): Uri {
        val base = safeName(name)
        val existing = folder.listFiles().mapNotNull { it.name }.toSet()
        var candidate = "$base.$extension"
        var suffix = 2
        while (candidate in existing) { candidate = "$base (${suffix++}).$extension" }
        val document = folder.createFile(mime, candidate) ?: error("无法创建文件，请检查文件夹授权和剩余空间。")
        try {
            context.contentResolver.openOutputStream(document.uri, "w")?.use { output ->
                temporary.inputStream().use { copy(it, output) }
            } ?: error("无法写入文件，请重新选择文件夹。")
            return document.uri
        } catch (error: Exception) {
            runCatching { document.delete() }
            throw error
        }
    }

    companion object {
        internal fun safeName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim().trim('.').take(60).trimEnd { it.isHighSurrogate() }.ifBlank { "未命名" }

        private fun copy(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("下载已取消")
                val size = input.read(buffer)
                if (size < 0) return
                output.write(buffer, 0, size)
            }
        }
    }
}
