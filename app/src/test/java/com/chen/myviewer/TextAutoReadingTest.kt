package com.chen.myviewer

import android.app.Activity
import android.os.Looper
import com.chen.myviewer.data.ReaderSettings
import com.chen.myviewer.reader.text.TextParser
import com.chen.myviewer.reader.text.TextReaderView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TextAutoReadingTest {
    @Test fun automaticPageTurnsUseBothAnimationsAndStopAtTheEnd() = withView { view ->
        val doc = TextParser.fromText("这是一段自动阅读的小说正文，用于验证按页向前阅读。\n".repeat(80))
        for (animation in listOf("SLIDE", "CURL")) {
            view.configure(doc, ReaderSettings(animation = animation), "PAGE", 0); view.goTo(0)
            assertTrue(view.readyForAutoReading)
            var pages = 0
            var before = view.currentOffset
            while (view.nextAutomatically()) {
                if (view.currentOffset == before) assertFalse("Pending page animations must postpone the next timer", view.readyForAutoReading)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
                assertTrue("$animation failed to advance from $before to ${view.currentOffset}", view.currentOffset > before)
                assertTrue(view.readyForAutoReading)
                before = view.currentOffset
                assertTrue(++pages < 100)
            }
            assertTrue(pages > 0)
            val last = view.currentOffset
            assertFalse(view.nextAutomatically())
            assertEquals(last, view.currentOffset)
        }
    }

    @Test fun automaticScrollAdvancesStopsAndFlushesTheLastVisibleAnchor() = withView { view ->
        val doc = TextParser.fromText("这是一段自动滚动的小说正文，阅读位置应跟随实际显示的文字。\n".repeat(120))
        var saved = -1
        view.onPosition = { offset, forced -> if (forced) saved = offset }
        view.configure(doc, ReaderSettings(), "SCROLL", 0)
        var steps = 0
        while (view.scrollAutomatically(80f)) assertTrue(++steps < 1000)
        assertTrue(view.currentOffset > 0)
        val last = view.currentOffset
        assertFalse(view.scrollAutomatically(80f))
        view.flush()
        assertEquals(last, view.currentOffset)
        assertEquals(last, saved)
    }

    private fun withView(test: (TextReaderView) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = TextReaderView(controller.get())
        controller.get().setContentView(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        view.layout(0, 0, 400, 700)
        try { test(view) } finally { controller.pause().stop().destroy() }
    }
}
