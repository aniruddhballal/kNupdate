package com.knupdate.app

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ActivityTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun liveDraftUsesTheButtonPressTimeEvenAfterWaitingAcrossMidnight() {
        val displayed = Instant.parse("2026-10-26T18:29:00.100Z").toEpochMilli()
        val pressedAt = Instant.parse("2026-10-26T18:31:34.567Z").toEpochMilli()
        val selected = ActivityTime.resolve(null, null, pressedAt)
        assertNotEquals(displayed, selected.startAt)
        assertEquals(pressedAt, selected.startAt)
        assertNull(selected.endAt)
        assertEquals("27 Oct 2026, 12:01:34 AM", ActivityTime.text(selected.startAt, zone, true))
    }

    @Test fun explicitSingleAndRangeTimesStayFixedWhenSendingLater() {
        val start = Instant.parse("2026-10-26T06:10:00Z").toEpochMilli()
        val end = start + 60_000
        val pressedAt = end + 120_567
        assertEquals(SelectedActivityTime(start, null), ActivityTime.resolve(start, null, pressedAt))
        assertEquals(SelectedActivityTime(start, end), ActivityTime.resolve(start, end, pressedAt))
        assertNull(ActivityTime.error(start, end, pressedAt))
    }

    @Test fun nowCapturesTheClockWithoutDiscardingSecondsOrMilliseconds() {
        val instant = Instant.parse("2026-10-26T06:12:34.567Z")
        val selected = ActivityTime.now(Clock.fixed(instant, zone))
        assertEquals(instant.toEpochMilli(), selected)
        assertEquals("11:42:34 AM", ActivityTime.text(selected, zone))
    }

    @Test fun manuallySelectedTimeHasExactlyMinutePrecision() {
        val selected = ActivityTime.manual(LocalDate.of(2026, 10, 26), 11 * 60 + 42, zone)
        assertEquals(Instant.parse("2026-10-26T06:12:00Z").toEpochMilli(), selected)
        assertEquals("11:42 AM", ActivityTime.text(selected, zone))
    }

    @Test fun datesAreRetainedAcrossMidnightRatherThanRebuiltOnSend() {
        val start = ActivityTime.now(Clock.fixed(Instant.parse("2026-10-26T18:29:59.900Z"), zone))
        val end = ActivityTime.now(Clock.fixed(Instant.parse("2026-10-26T18:30:01.100Z"), zone))
        assertEquals(LocalDate.of(2026, 10, 26), ActivityTime.date(start, zone))
        assertEquals(LocalDate.of(2026, 10, 27), ActivityTime.date(end, zone))
        assertNull(ActivityTime.error(start, end, end))
        assertEquals("26 Oct 2026, 11:59:59 PM", ActivityTime.text(start, zone, true))
    }

    @Test fun durationsWithinOneMinuteAreValidButReversedOrFutureTimesAreNot() {
        assertNull(ActivityTime.error(10_123L, 10_789L, 10_789L))
        assertNull(ActivityTime.error(10_123L, null, 10_789L))
        assertEquals("End must be after start.", ActivityTime.error(10_789L, 10_123L, 20_000L))
        assertEquals("End must be after start.", ActivityTime.error(10_123L, 10_123L, 20_000L))
        assertEquals("Choose a past time.", ActivityTime.error(20_001L, null, 20_000L))
        assertEquals("Choose a past time.", ActivityTime.error(10_000L, 20_001L, 20_000L))
    }
}
