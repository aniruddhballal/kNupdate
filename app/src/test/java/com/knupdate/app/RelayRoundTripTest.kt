package com.knupdate.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URL
import java.util.UUID

/** Opt-in test against an otherwise unused local or deployed relay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayRoundTripTest {
    @Test fun androidClientsStoreBeforeAcknowledgingThroughRealRelay() {
        val url = System.getenv("RELAY_TEST_URL")
        assumeTrue("Set RELAY_TEST_URL to exercise the real relay", !url.isNullOrBlank())
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            val names = listOf("test-sender.db", "test-first.db", "test-second.db")
            names.forEach { context.deleteDatabase(it) }
            val senderStore = ActivityStore(context, names[0])
            val firstStore = ActivityStore(context, names[1])
            val secondStore = ActivityStore(context, names[2])
            val firstSession = UUID.randomUUID().toString()
            val secondSession = UUID.randomUUID().toString()
            val sender = RelayClient(url!!)
            val firstClient = RelayClient(url)
            val secondClient = RelayClient(url)
            fun pendingCount(): Int = URL("$url/v1/updates").openStream().bufferedReader().use {
                JSONObject(it.readText()).getJSONArray("updates").length()
            }
            try {
                sender.health()
                val firstPoll = async(Dispatchers.IO) { firstClient.poll(firstSession) }
                val secondPoll = async(Dispatchers.IO) { secondClient.poll(secondSession) }
                delay(2_000)
                val record = ActivityRecord(title = "Run", body = "5k", startAt = System.currentTimeMillis() - 1_200_000,
                    endAt = System.currentTimeMillis() - 1, timeZone = "Asia/Calcutta")
                assertTrue(senderStore.insert(record))
                val result = sender.publish(record, null)
                assertEquals(2, result.getInt("listeners"))
                senderStore.updateStatus(record.id, "sent", 2)
                val first = ActivityRecord.fromWire(firstPoll.await().getJSONObject(0))
                val second = ActivityRecord.fromWire(secondPoll.await().getJSONObject(0))
                assertTrue(firstStore.insert(first))
                firstStore.close()
                val reopened = ActivityStore(context, names[1])
                reopened.load()
                assertEquals(record.id, reopened.records.value.single().id)
                assertFalse(reopened.insert(first))
                reopened.close()
                firstClient.acknowledge(firstSession, listOf(first.id))
                assertEquals(1, pendingCount())
                assertTrue(secondStore.insert(second))
                secondClient.acknowledge(secondSession, listOf(second.id))
                assertEquals(0, pendingCount())
                assertEquals("sent", senderStore.records.value.single().status)
                assertEquals("received", secondStore.records.value.single().status)
            } finally {
                firstClient.cancelPoll()
                secondClient.cancelPoll()
                runCatching { firstClient.close(firstSession) }
                runCatching { secondClient.close(secondSession) }
                senderStore.close(); firstStore.close(); secondStore.close()
                names.forEach { context.deleteDatabase(it) }
            }
        }
    }
}
