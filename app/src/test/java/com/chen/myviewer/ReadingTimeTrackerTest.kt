package com.chen.myviewer

import com.chen.myviewer.data.ReadingTimeRecord
import com.chen.myviewer.data.ReadingTimeTracker
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReadingTimeTrackerTest {
    private class Clock(start: String = "2026-10-29T12:00:00Z", val zone: ZoneId = ZoneId.of("Asia/Shanghai")) {
        var elapsed = 0L
        var wall = Instant.parse(start).toEpochMilli()
        val writes = mutableListOf<List<ReadingTimeRecord>>()
        val tracker = ReadingTimeTracker({ writes += it }, { elapsed }, { wall }, { zone })
        fun advance(ms: Long) { elapsed += ms; wall += ms }
        fun totals() = writes.flatten().groupBy { it.sessionId to it.day }.mapValues { (_, rows) -> rows.maxOf { it.durationMs } }
    }

    @Test fun checkpointsPauseAndResumeUseCumulativeTimeWithoutCountingThePause() {
        val clock = Clock()
        clock.tracker.visible("session", "book", true)
        clock.advance(1234); clock.tracker.checkpoint(); clock.tracker.checkpoint()
        assertEquals(1234, clock.writes.last().single().durationMs)
        clock.advance(66); clock.tracker.visible("session", "book", false)
        assertEquals(1300, clock.writes.last().single().durationMs)
        val count = clock.writes.size
        clock.advance(120000); clock.tracker.checkpoint(); clock.tracker.visible("session", "book", false)
        assertEquals(count, clock.writes.size)
        clock.tracker.visible("session", "book", true)
        clock.advance(2700); clock.tracker.stop(); clock.tracker.stop()
        assertEquals(4000, clock.totals().values.sum())
    }

    @Test fun midnightSplitsDaysAndClockChangesDoNotChangeElapsedDuration() {
        val clock = Clock("2026-10-29T15:59:57Z")
        clock.tracker.visible("session", "book", true)
        clock.advance(8000); clock.tracker.stop()
        assertEquals(3000L, clock.totals()["session" to "2026-10-29"])
        assertEquals(5000L, clock.totals()["session" to "2026-10-30"])
        val dst = Clock("2026-11-01T05:59:59Z", ZoneId.of("America/New_York"))
        dst.tracker.visible("dst", "book", true); dst.advance(2000); dst.tracker.stop()
        assertEquals(2000L, dst.totals()["dst" to "2026-11-01"])
    }

    @Test fun editingWallClockNeverCreatesHoursOfReadingTime() {
        val clock = Clock()
        clock.tracker.visible("session", "book", true)
        clock.elapsed += 1000; clock.wall += 86_400_000
        clock.tracker.checkpoint()
        clock.elapsed += 2000; clock.wall -= 172_800_000
        clock.tracker.stop()
        assertEquals(3000L, clock.totals().values.sum())
    }

    @Test fun loadingAndBookSwitchesDoNotMixSessions() {
        val clock = Clock()
        clock.advance(30000); clock.tracker.checkpoint()
        assertTrue(clock.writes.isEmpty())
        clock.tracker.visible("one", "book-one", true)
        clock.advance(2000)
        clock.tracker.visible("two", "book-two", true)
        clock.advance(500); clock.tracker.stop()
        assertEquals(2000L, clock.writes.flatten().filter { it.bookId == "book-one" }.maxOf { it.durationMs })
        assertEquals(500L, clock.writes.flatten().filter { it.bookId == "book-two" }.maxOf { it.durationMs })
        assertEquals(2500L, clock.totals().values.sum())
    }
}
