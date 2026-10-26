package com.knupdate.app

import org.junit.Assert.*
import org.junit.Test

class ActivityCatalogTest {
    @Test fun catalogHasUniquePredefinedChoicesWithinTheRelayLimits() {
        assertEquals(100, ACTIVITY_OPTIONS.size)
        assertEquals(100, ACTIVITY_OPTIONS.map { it.id }.toSet().size)
        assertEquals("Each activity needs a distinct icon", 100, ACTIVITY_OPTIONS.map { it.icon }.toSet().size)
        assertEquals(10, ACTIVITY_GROUPS.map { it.id }.toSet().size)
        ACTIVITY_OPTIONS.forEach { activity ->
            assertTrue(activity.title.isNotBlank() && activity.title.length <= 80)
            assertTrue(activity.icon != 0)
            assertTrue(activity.bodies.isNotEmpty())
            assertEquals(activity.bodies.size, activity.bodies.toSet().size)
            assertTrue(activity.bodies.all { it.isNotBlank() && it.length <= 280 })
        }
    }

    @Test fun eventsDefaultToOneTimestampAndDurationsToTwo() {
        val moments = setOf("location", "waking", "drinking", "arrived", "departed", "collecting", "delivery", "medicine", "check-in")
        assertEquals(moments, ACTIVITY_OPTIONS.filter { !it.isRange }.map { it.id }.toSet())
        assertTrue(ACTIVITY_OPTIONS.filter { it.id in setOf("eating", "working", "studying", "run", "sleeping") }.all { it.isRange })
    }
}
