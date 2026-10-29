package com.chen.myviewer.data

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

/** Cumulative checkpoints make duplicate or out-of-order writes safe. */
class ReadingTimeRepository(private val dao: ViewerDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val errors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun record(records: List<ReadingTimeRecord>) {
        if (records.isEmpty()) return
        scope.launch { runCatching { dao.saveReadingTimes(records) }.onFailure { errors.tryEmit(Unit) } }
    }
}

class ReadingTimeTracker(
    private val record: (List<ReadingTimeRecord>) -> Unit,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val wall: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private var sessionId = ""
    private var bookId = ""
    private var active = false
    private var lastElapsed = 0L
    private var lastWall = 0L
    private var lastZone = zone()
    private val days = linkedMapOf<String, Long>()

    fun visible(session: String, book: String, visible: Boolean) {
        if (sessionId == session && bookId == book && !active && !visible) return
        checkpoint()
        if (sessionId != session || bookId != book) {
            sessionId = session; bookId = book; days.clear()
        }
        active = visible
    }

    fun checkpoint() {
        val nowElapsed = elapsed(); val nowWall = wall()
        if (active) {
            var remaining = (nowElapsed - lastElapsed).coerceAtLeast(0)
            // Wall-clock edits must never inflate the measured elapsed duration.
            var cursor = if (abs((nowWall - lastWall) - remaining) <= 1000L) lastWall else nowWall - remaining
            while (remaining > 0) {
                val date = Instant.ofEpochMilli(cursor).atZone(lastZone).toLocalDate()
                val boundary = date.plusDays(1).atStartOfDay(lastZone).toInstant().toEpochMilli()
                val part = min(remaining, (boundary - cursor).coerceAtLeast(1))
                val day = date.toString()
                days[day] = (days[day] ?: 0L) + part
                cursor += part; remaining -= part
            }
            if (days.isNotEmpty()) record(days.map { (day, duration) -> ReadingTimeRecord(sessionId, day, bookId, duration) })
        }
        lastElapsed = nowElapsed; lastWall = nowWall; lastZone = zone()
    }

    fun stop() { checkpoint(); active = false }
}
