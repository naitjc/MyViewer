package com.chen.myviewer.source

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Aidoku's wire format uses unsigned LEB128, zigzag signed integers and little-endian floats. */
internal class Postcard(private val bytes: ByteArray) {
    var offset = 0
        private set
    fun byte(): Int { require(offset < bytes.size) { "漫画源返回的数据不完整。" }; return bytes[offset++].toInt() and 255 }
    fun unsigned(): Long {
        var result = 0L
        for (shift in 0..63 step 7) {
            val value = byte()
            require(shift != 63 || value <= 1) { "漫画源整数溢出。" }
            result = result or ((value and 127).toLong() shl shift)
            if (value < 128) return result
        }
        error("漫画源整数编码无效。")
    }
    fun count(): Int = unsigned().also { require(it in 0..100_000) { "漫画源列表过大。" } }.toInt()
    fun boolean(): Boolean = when (byte()) { 0 -> false; 1 -> true; else -> error("漫画源布尔编码无效。") }
    fun string(): String {
        val length = unsigned().also { require(it in 0..16_777_216) }.toInt()
        require(length <= bytes.size - offset) { "漫画源字符串不完整。" }
        return bytes.copyOfRange(offset, offset + length).toString(Charsets.UTF_8).also { offset += length }
    }
    fun float(): Float {
        require(bytes.size - offset >= 4)
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float.also { offset += 4 }
    }
    fun <T> optional(read: () -> T): T? = if (boolean()) read() else null
    fun <T> list(read: () -> T): List<T> = List(count()) { read() }
    fun signed(): Long { val value = unsigned(); return (value ushr 1) xor -(value and 1) }
    fun complete() { require(offset == bytes.size) { "漫画源数据版本暂不兼容。" } }

    fun manga(): SourceManga {
        val start = offset
        val key = string(); val title = string(); val cover = optional(::string)
        optional { list(::string) } // artists
        val authors = optional { list(::string) }.orEmpty()
        val description = optional(::string); val url = optional(::string)
        val tags = optional { list(::string) }.orEmpty()
        unsigned(); unsigned() // status and content rating
        val viewer = unsigned().toInt()
        unsigned(); optional(::signed) // update strategy and next update time
        val chapters = optional { list(::chapter) }
        return SourceManga(key, title, cover, authors, description, url, tags, viewer, chapters, bytes.copyOfRange(start, offset))
    }
    fun chapter(): SourceChapter {
        val start = offset
        val key = string(); val title = optional(::string); val number = optional(::float)
        optional(::float); optional(::signed); optional { list(::string) }
        optional(::string); optional(::string); optional(::string)
        val locked = boolean()
        return SourceChapter(key, title, number, locked, bytes.copyOfRange(start, offset))
    }
    fun page(): SourcePage {
        require(unsigned() == 0L) { "此章节使用的文本、内嵌图片或 ZIP 页面格式尚未支持。" }
        val url = string()
        val context = optional { buildMap { repeat(count()) { put(string(), string()) } } }
        optional(::string); boolean(); optional(::string)
        return SourcePage(url, context)
    }
}

internal class PostcardWriter {
    private val output = ByteArrayOutputStream()
    fun unsigned(value: Long): PostcardWriter {
        var remaining = value
        do {
            val part = (remaining and 127).toInt(); remaining = remaining ushr 7
            output.write(part or if (remaining != 0L) 128 else 0)
        } while (remaining != 0L)
        return this
    }
    fun string(value: String): PostcardWriter { val bytes = value.toByteArray(); unsigned(bytes.size.toLong()); output.write(bytes); return this }
    fun map(value: Map<String, String>): PostcardWriter { unsigned(value.size.toLong()); value.forEach { (key, item) -> string(key); string(item) }; return this }
    fun float(value: Float): PostcardWriter {
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array())
        return this
    }
    fun bytes(): ByteArray = output.toByteArray()
}
