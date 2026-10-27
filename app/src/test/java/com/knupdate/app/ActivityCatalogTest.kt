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

    @Test fun auditedPoliciesKeepMomentEventsSingleAndOngoingActivitiesFlexible() {
        val moments = setOf("location", "waking", "arrived", "departed", "collecting", "delivery", "medicine", "check-in")
        assertEquals(moments, ACTIVITY_OPTIONS.filter { it.timePolicy == ActivityTimePolicy.AT_ONLY }.map { it.id }.toSet())
        assertEquals(92, ACTIVITY_OPTIONS.count { it.timePolicy == ActivityTimePolicy.BOTH })
        assertFalse(ACTIVITY_OPTIONS.any { it.timePolicy == ActivityTimePolicy.RANGE_ONLY })
        assertEquals(moments + "drinking", ACTIVITY_OPTIONS.filter { !it.defaultIsRange }.map { it.id }.toSet())
        ACTIVITY_OPTIONS.forEach { assertTrue(it.id, it.timePolicy.allows(it.defaultIsRange)) }
    }

    @Test fun mixedUseActivitiesSupportQuickSnapshotsAndLongerSessions() {
        for (id in setOf("drinking", "post-office", "fuel-charge", "bank", "health-visit", "pet-care", "photography")) {
            val activity = ACTIVITY_OPTIONS.single { it.id == id }
            assertEquals(id, ActivityTimePolicy.BOTH, activity.timePolicy)
        }
        val drink = ACTIVITY_OPTIONS.single { it.id == "drinking" }
        assertTrue(drink.bodies.containsAll(listOf("Water", "Coffee", "Tea")))
        assertFalse(drink.defaultIsRange)
        assertTrue(ACTIVITY_OPTIONS.filter { it.id in setOf("eating", "working", "studying", "run", "sleeping") }.all { it.defaultIsRange })
    }

    @Test fun unsupportedCatalogDefaultsCannotBeConstructed() {
        val work = ACTIVITY_OPTIONS.single { it.id == "working" }
        assertThrows(IllegalArgumentException::class.java) { work.copy(timePolicy = ActivityTimePolicy.AT_ONLY, defaultIsRange = true) }
        assertThrows(IllegalArgumentException::class.java) { work.copy(timePolicy = ActivityTimePolicy.RANGE_ONLY, defaultIsRange = false) }
    }
}
