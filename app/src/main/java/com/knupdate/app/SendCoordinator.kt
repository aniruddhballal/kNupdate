package com.knupdate.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SendCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSending = MutableStateFlow(false)
    val sending = mutableSending.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun send(context: Context, record: ActivityRecord) {
        if (!mutableSending.compareAndSet(false, true)) return
        mutableError.value = null
        val appContext = context.applicationContext
        scope.launch {
            try {
                val store = ActivityStore.get(appContext)
                // A durable local copy is required before any network request.
                if (!store.insert(record)) return@launch
                val url = RelaySettings.url()
                if (url.isBlank()) { store.updateStatus(record.id, "local"); return@launch }
                try {
                    val result = RelayClient(url).publish(record, ReceiverStatus.session)
                    store.updateStatus(record.id, "sent", result.getInt("listeners"))
                } catch (_: Exception) {
                    // A timeout could follow acceptance. Never queue or rebroadcast silently.
                    store.updateStatus(record.id, "uncertain")
                }
            } catch (_: Exception) {
                mutableError.value = "Could not save this activity on the phone. Check available storage."
            } finally { mutableSending.value = false }
        }
    }
}
