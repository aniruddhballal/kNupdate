package com.knupdate.app

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class SelectedActivityTime(val startAt: Long, val endAt: Long?)

/** Epoch milliseconds are kept throughout editing, storage and transport. */
internal object ActivityTime {
    fun now(clock: Clock = Clock.systemUTC()): Long = clock.millis()

    /** Null is the live draft: resolve it only in the send-button handler. */
    fun resolve(customStart: Long?, customEnd: Long?, pressedAt: Long): SelectedActivityTime =
        if (customStart == null) SelectedActivityTime(pressedAt, null) else SelectedActivityTime(customStart, customEnd)

    fun manual(date: LocalDate, minutes: Int, zone: ZoneId): Long {
        require(minutes in 0..1439)
        return date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()
    }

    fun minutes(timestamp: Long, zone: ZoneId): Int = Instant.ofEpochMilli(timestamp).atZone(zone).let {
        it.hour * 60 + it.minute
    }

    fun date(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    fun text(timestamp: Long, zone: ZoneId, includeDate: Boolean = false): String {
        val seconds = timestamp % 60_000L != 0L
        val pattern = (if (includeDate) "d MMM yyyy, " else "") + (if (seconds) "h:mm:ss a" else "h:mm a")
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(timestamp))
    }

    fun error(start: Long, end: Long?, now: Long): String? = when {
        start > now || (end != null && end > now) -> "Choose a past time."
        end != null && end <= start -> "End must be after start."
        else -> null
    }
}
