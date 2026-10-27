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
            val senderCredentials = DeviceCredentials(UUID.randomUUID().toString(), UUID.randomUUID().toString())
            val firstCredentials = DeviceCredentials(UUID.randomUUID().toString(), UUID.randomUUID().toString())
            val secondCredentials = DeviceCredentials(UUID.randomUUID().toString(), UUID.randomUUID().toString())
            val firstSession = UUID.randomUUID().toString()
            val secondSession = UUID.randomUUID().toString()
            val sender = RelayClient(url!!, senderCredentials)
            val firstClient = RelayClient(url, firstCredentials)
            val secondClient = RelayClient(url, secondCredentials)
            try {
                sender.health()
                sender.register("Test sender")
                firstClient.register("First listener")
                secondClient.register("Second listener")
                val invite = sender.invite().getString("code")
                firstClient.acceptInvite(invite)
                val firstPoll = async(Dispatchers.IO) { firstClient.poll(firstSession, "First listener") }
                val secondPoll = async(Dispatchers.IO) { secondClient.poll(secondSession, "Second listener") }
                delay(2_000)
                val record = ActivityRecord(title = "Current location", body = "Synthetic location test", startAt = System.currentTimeMillis() - 1_200_000,
                    endAt = null, timeZone = "Asia/Calcutta",
                    coordinates = Coordinates(12.345678, -76.54321, 24.5, System.currentTimeMillis()))
                assertTrue(senderStore.insert(record))
                val result = sender.publish(record, null, listOf(firstCredentials.id))
                assertEquals(1, result.getInt("listeners"))
                senderStore.recordAcceptance(record.id, result)
                assertEquals(listOf("First listener"), senderStore.records.value.single().recipients!!.map { it.name })
                val first = ActivityRecord.fromWire(firstPoll.await().getJSONObject(0))
                assertTrue(firstStore.insert(first))
                firstStore.close()
                val reopened = ActivityStore(context, names[1])
                reopened.load()
                assertEquals(record.id, reopened.records.value.single().id)
                assertEquals(record.coordinates, reopened.records.value.single().coordinates)
                assertFalse(reopened.insert(first))
                reopened.close()
                firstClient.acknowledge(firstSession, listOf(first.id))
                val firstReport = sender.receipts(record.id, record.receiptKey!!, -1)
                senderStore.applyReceiptReport(record.id, record.receiptKey, firstReport)
                assertEquals(1, senderStore.records.value.single().recipients!!.count { it.confirmedAt != null })
                assertEquals(1, firstReport.getInt("revision"))
                sender.forgetReceipts(record.id, record.receiptKey)
                senderStore.finishReceiptTracking(record.id)
                assertEquals("sent", senderStore.records.value.single().status)
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
