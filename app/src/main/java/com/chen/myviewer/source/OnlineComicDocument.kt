package com.chen.myviewer.source

import android.graphics.BitmapFactory
import android.util.LruCache
import com.chen.myviewer.reader.comic.ComicDocument
import com.chen.myviewer.reader.comic.ComicPage
import com.chen.myviewer.reader.comic.ComicPreviews
import com.chen.myviewer.reader.comic.ImageSize
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class OnlineComicDocument(
    private val repository: SourceRepository,
    val title: OnlineTitle,
    val chapter: SourceChapter,
    private val pages: List<SourcePage>,
) : ComicDocument {
    override val imageNames = pages.indices.map { it.toString().padStart(6, '0') }
    override val direction = if (title.manga.viewer == 2) "RTL" else "LTR"
    override val eagerSizeReads = false
    private val sizes = ConcurrentHashMap<Int, ImageSize>()
    private val locks = pages.map { Any() }
    private val previews = object : LruCache<Int, ComicPage>(16 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ComicPage) = value.preview.allocationByteCount
    }
    @Volatile private var closed = false
    override fun imageFile(index: Int): File {
        check(!closed) { "章节已经关闭。" }
        return repository.imageFile(title, chapter, pages, index)
    }
    override fun size(index: Int): ImageSize = sizes[index] ?: synchronized(locks[index]) {
        sizes[index] ?: run {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile(index).absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片无法解码，请重试或检查漫画源。" }
            ImageSize(bounds.outWidth, bounds.outHeight).also { sizes[index] = it }
        }
    }
    override fun cachedSize(index: Int) = sizes[index]
    override fun restoreIndex(entry: String, fallback: Int) = imageNames.indexOf(entry).takeIf { it >= 0 } ?: fallback.coerceIn(imageNames.indices)
    override fun cachedPage(index: Int): ComicPage? = synchronized(previews) { previews.get(index) }?.takeIf { it.file.exists() }
    override fun page(index: Int): ComicPage = synchronized(locks[index]) {
        cachedPage(index)?.let { return@synchronized it }
        val size = size(index)
        val file = imageFile(index)
        val page = ComicPage(file, size, ComicPreviews.decode(file, size))
        synchronized(previews) { if (!closed) previews.put(index, page) }
        page
    }
    override fun suggestMode() = if (title.manga.viewer in setOf(3, 4)) "SCROLL" else "PAGE"
    override fun close() { closed = true; synchronized(previews) { previews.evictAll() } }
}
