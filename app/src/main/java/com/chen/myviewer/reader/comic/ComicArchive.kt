package com.chen.myviewer.reader.comic

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.chen.myviewer.data.Book
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

object NaturalOrder : Comparator<String> {
    private val pieces = Regex("[0-9]+|[^0-9]+")
    override fun compare(a: String, b: String): Int {
        val left = pieces.findAll(a.lowercase()).map { it.value }.toList()
        val right = pieces.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]; val y = right[i]
            val compared = if (x[0].isDigit() && y[0].isDigit()) {
                val xn = x.trimStart('0').ifEmpty { "0" }; val yn = y.trimStart('0').ifEmpty { "0" }
                xn.length.compareTo(yn.length).takeIf { it != 0 } ?: xn.compareTo(yn)
            } else x.compareTo(y)
            if (compared != 0) return compared
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }
}

data class ComicImage(val name: String, val entry: ZipArchiveEntry)
data class ImageSize(val width: Int, val height: Int)

class ComicArchive private constructor(private val zip: ZipFile, private val directory: File) : Closeable {
    private val entrySizes = ConcurrentHashMap<ZipArchiveEntry, ImageSize>()
    private val imageIndex = ComicImageIndex(zip.entries.asSequence().filter {
        !it.isDirectory && !it.name.split('/').any { part -> part.startsWith('.') || part == "__MACOSX" } &&
            it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") && zip.canReadEntryData(it)
    }.map { ComicImage(it.name, it) }.toList()) { image -> runCatching { readSize(image) }.getOrNull() }
    val images: List<ComicImage> = imageIndex.images
    val direction: String = readDirection()
    private val lock = Any()
    private val previews = object : LruCache<Int, ComicPage>(16 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ComicPage) = value.preview.allocationByteCount
    }
    @Volatile private var closed = false

    fun imageFile(index: Int): File = synchronized(lock) {
        check(!closed) { "漫画已经关闭，请重新打开。" }
        val image = images[index.coerceIn(0, images.lastIndex)]
        val key = digest("${image.name}:${image.entry.crc}:${image.entry.size}")
        val target = File(directory, "$key.img")
        if (target.exists() && target.length() > 0) { target.setLastModified(System.currentTimeMillis()); return@synchronized target }
        require(image.entry.size <= 128L * 1024 * 1024) { "单张漫画图片超过 128 MB，暂时无法显示。" }
        directory.mkdirs()
        val pending = File(directory, "$key.part")
        try {
            zip.getInputStream(image.entry).use { input -> pending.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count
                    require(total <= 128L * 1024 * 1024) { "单张图片解压后过大。" }
                    output.write(buffer, 0, count)
                }
            } }
            require(pending.length() > 0) { "这张图片没有内容。" }
            check(pending.renameTo(target)) { "图片缓存写入失败。" }
        } finally { pending.delete() }
        // Maintenance must not hold up the next foreground image read.
        if (trimPending.compareAndSet(false, true)) maintenanceScope.launch {
            try { delay(750); runCatching { trimCache(target) } } finally { trimPending.set(false) }
        }
        target
    }
    private fun readSize(image: ComicImage): ImageSize = entrySizes[image.entry] ?: run {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(image.entry).buffered().use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "图片无法解码。" }
        ImageSize(options.outWidth, options.outHeight).also { entrySizes[image.entry] = it }
    }
    fun size(index: Int): ImageSize = entrySizes[images[index].entry] ?: synchronized(lock) {
        check(!closed) { "漫画已经关闭，请重新打开。" }
        // Read only the image header, without extracting the entire ZIP entry.
        readSize(images[index])
    }
    fun cachedSize(index: Int): ImageSize? = entrySizes[images[index].entry]
    fun restoreIndex(entry: String, fallback: Int): Int = imageIndex.restoreIndex(entry, fallback)
    fun cachedPage(index: Int): ComicPage? = synchronized(previews) { previews.get(index) }
    fun page(index: Int): ComicPage = synchronized(lock) {
        check(!closed) { "漫画已经关闭，请重新打开。" }
        cachedPage(index)?.takeIf { it.file.exists() }?.let { return@synchronized it }
        val size = size(index)
        val file = imageFile(index)
        ComicPage(file, size, ComicPreviews.decode(file, size)).also { synchronized(previews) { previews.put(index, it) } }
    }
    fun suggestMode(): String {
        val sample = images.indices.take(6).mapNotNull { runCatching { size(it) }.getOrNull() }
        return if (sample.isNotEmpty() && sample.count { it.height.toFloat() / it.width > 2.6f } * 2 > sample.size) "SCROLL" else "PAGE"
    }
    private fun readDirection(): String = runCatching {
        val entry = zip.entries.asSequence().firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", true) } ?: return@runCatching "LTR"
        if (entry.size > 256 * 1024) return@runCatching "LTR"
        val parser = Xml.newPullParser()
        zip.getInputStream(entry).use { input ->
            parser.setInput(input, null)
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "Manga" && parser.nextText() == "YesAndRightToLeft") return@runCatching "RTL"
                parser.next()
            }
        }
        "LTR"
    }.getOrDefault("LTR")
    override fun close() = synchronized(lock) { if (!closed) { closed = true; zip.close(); synchronized(previews) { previews.evictAll() } } }
    private fun trimCache(keep: File) {
        val root = directory.parentFile ?: return
        synchronized(cacheLock) {
            val files = root.walkTopDown().filter { it.isFile && it.extension == "img" }.toList()
            var size = files.sumOf { it.length() }
            if (size <= CACHE_LIMIT) return@synchronized
            files.asSequence().filter { it != keep }.sortedBy { it.lastModified() }.forEach { file ->
                if (size > CACHE_LIMIT) { val length = file.length(); if (file.delete()) size -= length }
            }
        }
    }

    companion object {
        private val cacheLock = Any()
        private val trimPending = AtomicBoolean(false)
        private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val CACHE_LIMIT = 256L * 1024 * 1024
        @Suppress("DEPRECATION")
        fun open(context: Context, book: Book): ComicArchive {
            val input = if (book.storage == "COPY") FileInputStream(requireNotNull(book.localPath)) else {
                val fd = context.contentResolver.openFileDescriptor(Uri.parse(book.sourceUri), "r") ?: error("文件无法打开，请重新定位原文件。")
                ParcelFileDescriptor.AutoCloseInputStream(fd)
            }
            try {
                val channel: SeekableByteChannel = input.channel
                try { channel.position(0) } catch (e: Exception) { throw IllegalStateException("这个来源不支持 ZIP 直接读取，请选择复制导入。", e) }
                val zip = ZipFile.builder().setSeekableByteChannel(channel).setCharset("GB18030").setUseUnicodeExtraFields(true).get()
                return ComicArchive(zip, File(context.cacheDir, "comic/${book.id}")).also {
                    if (it.images.isEmpty()) { it.close(); error("ZIP 中没有可读取的 JPG、PNG 或 WebP 图片；请确认压缩包未加密。") }
                }
            } catch (e: Exception) { input.close(); throw e }
        }
        fun digest(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            val chars = CharArray(bytes.size * 2); val hex = "0123456789abcdef"
            bytes.forEachIndexed { index, byte -> val value = byte.toInt() and 255; chars[index * 2] = hex[value ushr 4]; chars[index * 2 + 1] = hex[value and 15] }
            return String(chars)
        }
    }
}

