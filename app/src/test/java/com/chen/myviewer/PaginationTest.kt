package com.chen.myviewer

import android.text.TextPaint
import com.chen.myviewer.reader.text.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PaginationTest {
    @Test fun pagesCoverTextWithoutGapsAndPreviousReturnsSamePage() {
        val doc = TextParser.fromText((1..300).joinToString("\n") { "第 $it 行，这是一段需要进行换行的中文内容，检查分页位置。" })
        val engine = TextPaginator(doc, TextPaint().apply { textSize = 22f }, 280, 400, 1.6f)
        var page = engine.pageAt(0)!!
        var end = 0; var count = 0
        while (true) {
            assertEquals(end, page.start); assertTrue(page.end > page.start)
            assertEquals(page.start, engine.pageAt(page.start)!!.start)
            end = page.end; count++
            val next = engine.adjacent(page, true) ?: break
            assertEquals(page.start, engine.adjacent(next, false)!!.start)
            page = next
            check(count < 10000)
        }
        assertEquals(doc.text.length, end); assertTrue(count > 1)
    }
    @Test fun anchorSurvivesFontChangeAndChunkBoundariesDoNotSplitSurrogates() {
        val doc = TextParser.fromText("风🌙".repeat(9000))
        val small = TextPaginator(doc, TextPaint().apply { textSize = 18f }, 300, 500, 1.5f)
        val large = TextPaginator(doc, TextPaint().apply { textSize = 30f }, 300, 500, 1.5f)
        val anchor = 12457
        for (engine in listOf(small, large)) {
            val page = engine.pageAt(anchor)!!
            assertTrue(anchor in page.start until page.end)
            engine.chunks.dropLast(1).forEach { assertFalse(Character.isHighSurrogate(doc.text[it.end - 1])) }
        }
    }
}

