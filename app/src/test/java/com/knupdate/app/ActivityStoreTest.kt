package com.knupdate.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityStoreTest {
    private lateinit var context: Context
    private lateinit var store: ActivityStore

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("activities.db")
        context.getSharedPreferences("knupdate_local", 0).edit().clear().commit()
        store = ActivityStore(context)
    }

    @After fun cleanup() { store.close() }

    private fun record(direction: String = "sent") = ActivityRecord(
        title = "Run", body = "5k", startAt = System.currentTimeMillis() - 1_200_000,
        endAt = System.currentTimeMillis() - 1, timeZone = "Asia/Calcutta", direction = direction,
        status = if (direction == "received") "received" else "sending")

    @Test fun sentHistorySurvivesReopenAndKeepsDeliveryStatus() {
        val activity = record()
        assertTrue(store.insert(activity))
        store.updateStatus(activity.id, "sent", 2)
        store.close()
        store = ActivityStore(context)
        store.load()
        val recovered = store.records.value.single()
        assertEquals(activity.id, recovered.id)
        assertEquals("sent", recovered.status)
        assertEquals(2, recovered.listeners)
        assertEquals(activity.startAt, recovered.startAt)
    }

    @Test fun receivedReplayIsDeduplicatedAfterReopen() {
        val activity = record("received")
        assertTrue(store.insert(activity))
        store.close()
        store = ActivityStore(context)
        assertFalse(store.insert(activity))
        assertEquals(1, store.records.value.size)
        assertEquals("received", store.records.value.single().direction)
    }

    @Test fun interruptedSendBecomesUncertainAndDoesNotQueueResending() {
        val activity = record()
        store.insert(activity)
        store.load() // Re-entering the screen must not mark an active send uncertain.
        assertEquals("sending", store.records.value.single().status)
        store.close()
        store = ActivityStore(context)
        store.load()
        assertEquals("uncertain", store.records.value.single().status)
    }

    @Test fun historyIsNotSilentlyTruncatedAtFiftyEntries() {
        repeat(75) { store.insert(record()) }
        store.close()
        store = ActivityStore(context)
        store.load()
        assertEquals(75, store.records.value.size)
    }

    @Test fun existingLocalUpdatesMigrateOnceWithoutInventingDates() {
        val old = JSONObject().put("title", "Run").put("body", "5k").put("start", 620).put("end", 640)
        context.getSharedPreferences("knupdate_local", 0).edit()
            .putString("updates", JSONArray().put(old).toString()).commit()
        store.load()
        assertEquals("Run", store.records.value.single().title)
        assertTrue(store.records.value.single().legacy)
        assertTrue(store.records.value.single().timeText().contains("date unavailable"))
        assertNull(context.getSharedPreferences("knupdate_local", 0).getString("updates", null))
        store.close()
        store = ActivityStore(context)
        store.load()
        assertEquals(1, store.records.value.size)
    }

    @Test fun networkSchemaContainsOnlyActivityFields() {
        context.getSharedPreferences("knupdate_local", 0).edit().putString("display_name", "Private Name").commit()
        val wire = record().wireJson()
        assertEquals(setOf("id", "title", "body", "startAt", "endAt", "timeZone"), wire.keys().asSequence().toSet())
        assertFalse(wire.toString().contains("Private Name"))
        assertEquals("received", ActivityRecord.fromWire(wire).direction)
    }

    @Test fun singleTimestampRoundTripsWithoutInventingAnEndTime() {
        val activity = record().copy(endAt = null)
        val restored = ActivityRecord.fromWire(activity.wireJson())
        assertNull(restored.endAt)
        assertEquals(activity.startAt, restored.startAt)
    }

    @Test fun malformedReceivedActivityIsRejectedBeforeStorage() {
        for (wire in listOf(record().wireJson().put("title", ""), record().wireJson().put("timeZone", "invalid"),
            record().wireJson().put("id", "invalid"), record().wireJson().put("endAt", 0))) {
            assertThrows(Exception::class.java) { ActivityRecord.fromWire(wire) }
        }
        assertTrue(store.records.value.isEmpty())
    }

    @Test fun relayUrlRequiresSecureBaseUrlAndRejectsCredentials() {
        assertEquals("https://relay.example", RelaySettings.normalizeUrl(" https://relay.example/ "))
        assertEquals("http://10.0.2.2:8787", RelaySettings.normalizeUrl("http://10.0.2.2:8787"))
        for (url in listOf("http://public.example", "https://user:pass@relay.example", "https://relay.example/v1", "https://relay.example?token=secret")) {
            assertThrows(Exception::class.java) { RelaySettings.normalizeUrl(url) }
        }
    }

    @Test fun legacyPhonePreferenceCannotOverrideTheBuiltInRelay() {
        val preferences = context.getSharedPreferences("relay", 0)
        preferences.edit().putString("url", "https://old-relay.example").commit()
        try {
            assertEquals(BuildConfig.RELAY_URL, RelaySettings.url())
        } finally { preferences.edit().clear().commit() }
    }
}
