package com.chen.myviewer

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
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
class TextGestureTest {
    @Test fun bothAnimationsCommitCompletedTurnAndKeepCancelledTurnAtOriginalPage() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = TextReaderView(controller.get())
        controller.get().setContentView(view); view.layout(0, 0, 400, 700)
        val doc = TextParser.fromText("这是一段用于验证翻页和书签锚点的文字。\n".repeat(300))
        for (animation in listOf("SLIDE", "CURL")) {
            view.configure(doc, ReaderSettings(animation = animation), "PAGE", 0); view.goTo(0)
            val start = SystemClock.uptimeMillis()
            event(view, start, start, MotionEvent.ACTION_DOWN, 330f)
            event(view, start, start + 300, MotionEvent.ACTION_MOVE, 300f)
            event(view, start, start + 600, MotionEvent.ACTION_UP, 300f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals("Cancelled $animation", 0, view.currentOffset)
            val tap = SystemClock.uptimeMillis()
            event(view, tap, tap, MotionEvent.ACTION_DOWN, 350f)
            event(view, tap, tap + 100, MotionEvent.ACTION_UP, 350f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertTrue("Completed $animation", view.currentOffset > 0)
        }
        controller.pause().stop().destroy()
    }
    private fun event(view: TextReaderView, down: Long, now: Long, type: Int, x: Float) {
        val event = MotionEvent.obtain(down, now, type, x, 350f, 0)
        view.onTouchEvent(event); event.recycle()
    }
}
