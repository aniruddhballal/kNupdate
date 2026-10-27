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

    @Test fun listenerSnapshotAndMonotonicConfirmationsPersistOnlyInLocalHistory() {
        val sent = record()
        store.insert(sent)
        val first = ListenerReceipt(java.util.UUID.randomUUID().toString(), "Alex")
        val second = ListenerReceipt(java.util.UUID.randomUUID().toString(), "Alex")
        val anonymous = ListenerReceipt(java.util.UUID.randomUUID().toString(), null)
        val snapshot = listOf(first, second, anonymous)
        store.recordAcceptance(sent.id, JSONObject().put("listeners", 3)
            .put("recipients", JSONArray(ListenerReceipt.json(snapshot)))
            .put("receiptExpiresAt", System.currentTimeMillis() + 30_000))
        val confirmed = snapshot.map { if (it.id == first.id) it.copy(confirmedAt = System.currentTimeMillis()) else it }
        store.applyReceiptReport(sent.id, sent.receiptKey!!, JSONObject().put("recipients", JSONArray(ListenerReceipt.json(confirmed))))
        store.close()
        store = ActivityStore(context)
        store.load()
        assertEquals(confirmed, store.records.value.single().recipients)
        assertEquals(sent.receiptKey, store.records.value.single().receiptKey)
        // An older report must not undo a confirmation already committed locally.
        store.applyReceiptReport(sent.id, sent.receiptKey, JSONObject().put("recipients", JSONArray(ListenerReceipt.json(snapshot))))
        assertEquals(confirmed, store.records.value.single().recipients)
        val renamed = confirmed.map { it.copy(name = "Changed") }
        assertThrows(IllegalArgumentException::class.java) {
            store.applyReceiptReport(sent.id, sent.receiptKey, JSONObject().put("recipients", JSONArray(ListenerReceipt.json(renamed))))
        }
        val wire = store.records.value.single().wireJson().toString()
        assertFalse(wire.contains("Alex")); assertFalse(wire.contains(sent.receiptKey))
        assertFalse(wire.contains(first.id)); assertFalse(wire.contains("recipients"))
        val received = ActivityRecord.fromWire(store.records.value.single().wireJson())
        assertNull(received.receiptKey); assertNull(received.recipients)
        store.finishReceiptTracking(sent.id)
        assertNull(store.records.value.single().receiptKey)
        assertEquals(confirmed, store.records.value.single().recipients)
    }

    @Test fun olderRelayResponsesDoNotInventListenerNamesOrConfirmations() {
        val sent = record()
        store.insert(sent)
        store.recordAcceptance(sent.id, JSONObject().put("listeners", 2))
        val saved = store.records.value.single()
        assertEquals("sent", saved.status); assertEquals(2, saved.listeners)
        assertNull(saved.recipients); assertNull(saved.receiptKey); assertNull(saved.receiptExpiresAt)
    }

    @Test fun malformedAcceptanceCannotReplaceTheLocalSendWithFalseRecipients() {
        val sent = record()
        store.insert(sent)
        val recipient = ListenerReceipt(java.util.UUID.randomUUID().toString(), "Test name")
        assertThrows(IllegalArgumentException::class.java) {
            store.recordAcceptance(sent.id, JSONObject().put("listeners", 2)
                .put("recipients", JSONArray(ListenerReceipt.json(listOf(recipient)))))
        }
        assertEquals("sending", store.records.value.single().status)
        assertNull(store.records.value.single().recipients)
    }

    @Test fun versionTwoCoordinateHistoryUpgradesWithoutUploadingOrInventingNames() {
        store.close(); context.deleteDatabase("activities.db")
        val coordinates = Coordinates(0.0, 0.0, 20.0, 1000).json().toString()
        context.openOrCreateDatabase("activities.db", 0, null).use { db ->
            db.execSQL("""CREATE TABLE activities (id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
                start_at INTEGER NOT NULL, end_at INTEGER, time_zone TEXT NOT NULL, created_at INTEGER NOT NULL,
                direction TEXT NOT NULL, status TEXT NOT NULL, listeners INTEGER, legacy INTEGER NOT NULL DEFAULT 0, coordinates TEXT)""")
            db.execSQL("INSERT INTO activities VALUES ('old', 'Location', 'Home', 1000, NULL, 'UTC', 1000, 'sent', 'sent', 2, 0, ?)", arrayOf(coordinates))
            db.version = 2
        }
        store = ActivityStore(context); store.load()
        val old = store.records.value.single()
        assertEquals("old", old.id); assertEquals(2, old.listeners)
        assertEquals(0.0, old.coordinates!!.latitude, 0.0)
        assertNull(old.recipients); assertNull(old.receiptKey)
    }

    @Test fun olderLocationRangesRemainReadableWithoutRewritingTheirTimes() {
        val older = record().copy(title = "Current location", body = "Home")
        val policy = ACTIVITY_OPTIONS.single { it.id == "location" }.timePolicy
        assertNotNull(ActivityTime.selectionError(policy, older.startAt, older.endAt, System.currentTimeMillis()))
        // Older phones and existing history remain readable even though new drafts reject this shape.
        val received = ActivityRecord.fromWire(older.wireJson())
        assertTrue(store.insert(received))
        store.close()
        store = ActivityStore(context)
        store.load()
        val recovered = store.records.value.single()
        assertEquals(older.startAt, recovered.startAt)
        assertEquals(older.endAt, recovered.endAt)
        assertEquals("received", recovered.direction)
    }

    @Test fun coordinatesPersistInSenderAndReceiverWithoutChangingOtherActivities() {
        val fix = Coordinates(12.345678, -76.54321, 24.5, System.currentTimeMillis())
        val scheduled = record().copy(endAt = null, status = "scheduled")
        assertTrue(store.insert(scheduled))
        store.capture(scheduled.id, fix)
        val captured = store.records.value.single()
        assertEquals(fix, captured.coordinates)
        assertEquals("sending", captured.status)
        val received = ActivityRecord.fromWire(captured.wireJson())
        store.close()
        store = ActivityStore(context)
        store.load()
        assertEquals(fix, store.records.value.single().coordinates)
        store.close()
        context.deleteDatabase("coordinates-received.db")
        store = ActivityStore(context, "coordinates-received.db")
        assertTrue(store.insert(received))
        store.close()
        store = ActivityStore(context, "coordinates-received.db")
        store.load()
        assertEquals(fix, store.records.value.single().coordinates)
        assertFalse(store.insert(received))
        store.close()
        context.deleteDatabase("coordinates-received.db")
        store = ActivityStore(context)
        assertFalse(record().wireJson().has("coordinates"))
    }

    @Test fun senderNameFromRelayIsStoredForInboxAndNeverAddedToOutgoingWire() {
        val wire = JSONObject(record(direction = "received").wireJson().toString()).put("senderName", "Alice")
        val received = ActivityRecord.fromWire(wire)
        assertEquals("Alice", received.senderName)
        assertFalse(received.wireJson().has("senderName"))
        assertTrue(store.insert(received))
        store.close()
        store = ActivityStore(context)
        store.load()
        assertEquals("Alice", store.records.value.single().senderName)
    }

    @Test fun interruptedLocationSchedulesAreNotSentAfterReopening() {
        store.insert(record().copy(status = "scheduled"))
        store.insert(record().copy(status = "locating"))
        store.close()
        store = ActivityStore(context)
        store.load()
        assertTrue(store.records.value.all { it.status == "missed" && it.coordinates == null })
        assertEquals(2, store.records.value.size)
    }

    @Test fun versionOneHistoryUpgradesWithoutLosingEntries() {
        store.close()
        context.deleteDatabase("activities.db")
        context.openOrCreateDatabase("activities.db", 0, null).use { db ->
            db.execSQL("""CREATE TABLE activities (id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
                start_at INTEGER NOT NULL, end_at INTEGER, time_zone TEXT NOT NULL, created_at INTEGER NOT NULL,
                direction TEXT NOT NULL, status TEXT NOT NULL, listeners INTEGER, legacy INTEGER NOT NULL DEFAULT 0)""")
            db.execSQL("INSERT INTO activities VALUES ('old', 'Run', '5k', 1000, NULL, 'UTC', 1000, 'sent', 'sent', 2, 0)")
            db.version = 1
        }
        store = ActivityStore(context)
        store.load()
        assertEquals("old", store.records.value.single().id)
        assertEquals(2, store.records.value.single().listeners)
        assertNull(store.records.value.single().coordinates)
        val fix = Coordinates(0.0, 0.0, 50.0, System.currentTimeMillis())
        store.capture("old", fix)
        assertEquals(fix, store.records.value.single().coordinates)
    }

    @Test fun malformedCoordinatesAreRejectedBeforeStorage() {
        val value = record().copy(endAt = null)
        val fix = Coordinates(0.0, 0.0, 50.0, System.currentTimeMillis()).json()
        for (bad in listOf(JSONObject(fix.toString()).put("latitude", 91),
            JSONObject(fix.toString()).put("longitude", "0"), JSONObject(fix.toString()).put("accuracyMeters", -1),
            JSONObject(fix.toString()).put("capturedAt", value.startAt - 1),
            JSONObject(fix.toString()).put("capturedAt", System.currentTimeMillis() + 120_000),
            JSONObject(fix.toString()).put("name", "private"))) {
            assertThrows(Exception::class.java) { ActivityRecord.fromWire(value.wireJson().put("coordinates", bad)) }
        }
        assertThrows(Exception::class.java) { ActivityRecord.fromWire(value.wireJson().put("coordinates", JSONObject.NULL)) }
        val ranged = record().wireJson().put("coordinates", fix)
        assertNotNull(ActivityRecord.fromWire(ranged).endAt)
    }

    @Test fun exactTimesSurviveWireAndBothLocalHistories() {
        val exact = record().copy(startAt = 1_761_459_934_567L, endAt = 1_761_459_935_890L)
        assertTrue(store.insert(exact))
        val received = ActivityRecord.fromWire(exact.wireJson())
        assertEquals(exact.startAt, received.startAt)
        assertEquals(exact.endAt, received.endAt)
        assertTrue(received.timeText().contains(":34"))
        store.close()
        store = ActivityStore(context)
        store.load()
        assertEquals(exact.startAt, store.records.value.single().startAt)
        assertEquals(exact.endAt, store.records.value.single().endAt)
        store.close()
        store = ActivityStore(context, "received-precision.db")
        context.deleteDatabase("received-precision.db")
        assertTrue(store.insert(received))
        store.close()
        store = ActivityStore(context, "received-precision.db")
        store.load()
        assertEquals(exact.startAt, store.records.value.single().startAt)
        assertEquals(exact.endAt, store.records.value.single().endAt)
        store.close()
        context.deleteDatabase("received-precision.db")
    }

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
