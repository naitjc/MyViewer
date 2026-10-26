package com.chen.myviewer.reader.text

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

data class Chapter(val title: String, val offset: Int)
data class TextDocument(val text: String, val chapters: List<Chapter>, val encoding: String) {
    fun chapterAt(offset: Int): String = chapters.lastOrNull { it.offset <= offset }?.title ?: "正文"
    fun snippet(offset: Int): String = text.substring(offset.coerceIn(0, text.length), (offset + 70).coerceIn(0, text.length)).replace('\n', ' ').trim()
}

object TextParser {
    private const val MAX_BYTES = 48 * 1024 * 1024
    private val heading = Regex("^(?:第[零〇一二两三四五六七八九十百千万0-9０-９]+[章节回卷部集篇](?:[\\s　:：、.．].*|[^\\s　:：、.．]{0,25})?|(?:序章|序言|前言|楔子|引子|尾声|后记|终章|番外)(?:[\\s　:：、].*)?|chapter\\s+\\d+.*)$", RegexOption.IGNORE_CASE)
    fun parse(stream: InputStream, forced: String = "AUTO"): TextDocument {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "这本 TXT 超过首版支持的 48 MB，请先分卷后导入。" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.isNotEmpty()) { "这个 TXT 文件没有内容。" }
        return parseSource({ bytes.inputStream() }, forced)
    }
    /** Reopening the URI permits strict UTF-8 detection and a GB fallback without holding raw bytes in memory. */
    fun parseSource(open: () -> InputStream, forced: String = "AUTO"): TextDocument {
        val prefix = open().use { input -> buildList { repeat(3) { val value = input.read(); if (value >= 0) add(value) } } }
        val bom = when {
            prefix.take(3) == listOf(0xef, 0xbb, 0xbf) -> Charsets.UTF_8
            prefix.take(2) == listOf(0xff, 0xfe) -> Charsets.UTF_16LE
            prefix.take(2) == listOf(0xfe, 0xff) -> Charsets.UTF_16BE
            else -> null
        }
        var charset = if (forced != "AUTO") Charset.forName(forced) else bom ?: Charsets.UTF_8
        val text = try { decode(open, charset, forced == "AUTO" && bom == null) }
        catch (e: CharacterCodingException) { charset = Charset.forName("GB18030"); decode(open, charset, false) }
        require(text.isNotBlank()) { "这个 TXT 文件没有正文内容。" }
        require(text.count { it == '\u0000' } < text.length / 10 + 1) { "文件看起来不是普通 TXT，请检查文件或切换编码。" }
        return fromText(text, charset.name())
    }
    private fun decode(open: () -> InputStream, charset: Charset, strict: Boolean): String = open().use { source ->
        val limited = object : FilterInputStream(source) {
            var total = 0L
            private fun count(n: Int) { if (n > 0) { total += n; require(total <= MAX_BYTES) { "这本 TXT 超过首版支持的 48 MB，请先分卷后导入。" } } }
            override fun read(): Int { val n = source.read(); if (n >= 0) count(1); return n }
            override fun read(b: ByteArray, off: Int, len: Int): Int { val n = source.read(b, off, len); count(n); return n }
        }
        val decoder = charset.newDecoder().onMalformedInput(if (strict) CodingErrorAction.REPORT else CodingErrorAction.REPLACE).onUnmappableCharacter(if (strict) CodingErrorAction.REPORT else CodingErrorAction.REPLACE)
        val result = StringBuilder(); var lastCR = false
        InputStreamReader(limited, decoder).use { reader ->
            val chars = CharArray(8192)
            while (true) {
                val n = reader.read(chars); if (n < 0) break
                for (i in 0 until n) {
                    val char = chars[i]
                    if (result.isEmpty() && char == '\uFEFF') continue
                    if (char == '\n' && lastCR) { lastCR = false; continue }
                    result.append(if (char == '\r') '\n' else char); lastCR = char == '\r'
                }
                require(result.length <= 24 * 1024 * 1024) { "这本 TXT 的文字量超过首版上限，请先分卷。" }
            }
        }
        result.toString()
    }
    fun fromText(text: String, encoding: String = "UTF-8"): TextDocument {
        val chapters = mutableListOf<Chapter>()
        var offset = 0
        text.lineSequence().forEach { line ->
            val title = line.trim()
            if (title.length in 2..45 && heading.matches(title)) chapters += Chapter(title, offset)
            offset += line.length + 1
        }
        return TextDocument(text, chapters, encoding)
    }
}
