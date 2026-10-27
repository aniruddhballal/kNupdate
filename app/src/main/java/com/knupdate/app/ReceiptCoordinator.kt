package com.knupdate.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Safe GET retries collect acknowledgements; an activity POST is never retried. */
object ReceiptCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = mutableSetOf<String>()

    fun resume(context: Context) {
        ActivityStore.get(context).records.value.filter { it.receiptKey != null && it.receiptExpiresAt != null }.forEach { track(context, it.id) }
    }

    fun track(context: Context, id: String) {
        synchronized(active) { if (!active.add(id)) return }
        val app = context.applicationContext
        scope.launch {
            val store = ActivityStore.get(app)
            try {
                val record = store.records.value.firstOrNull { it.id == id } ?: return@launch
                val key = record.receiptKey ?: return@launch
                val until = record.receiptExpiresAt ?: return@launch
                val client = RelayClient(RelaySettings.url())
                var revision = -1
                while (System.currentTimeMillis() < until) {
                    try {
                        val result = client.receipts(id, key, revision)
                        store.applyReceiptReport(id, key, result)
                        revision = result.getInt("revision")
                        if (result.getBoolean("complete")) {
                            runCatching { client.forgetReceipts(id, key) }
                            break
                        }
                        if (result.optBoolean("expired")) break
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (error is RelayHttpException && error.statusCode == 404) break
                        delay(1_000)
                    }
                }
                store.finishReceiptTracking(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep the last committed local receipt state if storage becomes unavailable. */ }
            finally { synchronized(active) { active.remove(id) } }
        }
    }
}
