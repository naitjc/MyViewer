package com.chen.myviewer.reader.text

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

data class TextChunk(val start: Int, val end: Int)
data class TextPage(val start: Int, val end: Int, val layout: StaticLayout)
data class ChunkLayout(val chunk: TextChunk, val layout: StaticLayout, val pages: List<TextPage>)

class TextPaginator(val document: TextDocument, val paint: TextPaint, val width: Int, val height: Int, private val spacing: Float) {
    val chunks: List<TextChunk> = buildList {
        var start = 0
        while (start < document.text.length) {
            var end = minOf(start + 8000, document.text.length)
            if (end < document.text.length) {
                val newline = document.text.lastIndexOf('\n', end - 1)
                if (newline > start + 4000) end = newline + 1
                if (end > start && Character.isHighSurrogate(document.text[end - 1])) end--
            }
            add(TextChunk(start, end)); start = end
        }
    }
    private val cache = object : LinkedHashMap<Int, ChunkLayout>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ChunkLayout>?) = size > 5
    }
    fun chunkIndex(offset: Int): Int {
        var low = 0; var high = chunks.lastIndex
        while (low < high) { val mid = (low + high) ushr 1; if (chunks[mid].end <= offset) low = mid + 1 else high = mid }
        return low.coerceAtLeast(0)
    }
    fun chunk(index: Int): ChunkLayout = cache.getOrPut(index) {
        val chunk = chunks[index]
        val content = document.text.substring(chunk.start, chunk.end)
        val whole = layout(content)
        val pages = mutableListOf<TextPage>()
        var line = 0
        while (line < whole.lineCount) {
            val first = line; val top = whole.getLineTop(first)
            while (line + 1 < whole.lineCount && whole.getLineBottom(line + 1) - top <= height) line++
            val start = whole.getLineStart(first); val end = whole.getLineEnd(line)
            if (end > start) pages += TextPage(chunk.start + start, chunk.start + end, layout(content.substring(start, end)))
            line++
        }
        ChunkLayout(chunk, whole, pages)
    }
    private fun layout(content: String): StaticLayout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width.coerceAtLeast(1))
        // Builder defaults to simple line breaking and no hyphenation on all supported API levels.
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0f, spacing).build()
    fun pageAt(offset: Int): TextPage? {
        if (chunks.isEmpty()) return null
        val safe = offset.coerceIn(0, (document.text.length - 1).coerceAtLeast(0))
        return chunk(chunkIndex(safe)).pages.firstOrNull { it.end > safe } ?: chunk(chunkIndex(safe)).pages.lastOrNull()
    }
    fun adjacent(page: TextPage, next: Boolean): TextPage? = when {
        next && page.end < document.text.length -> pageAt(page.end)
        !next && page.start > 0 -> pageAt(page.start - 1)
        else -> null
    }
}

