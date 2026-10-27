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

    fun send(context: Context, record: ActivityRecord, recipientIds: List<String>, requireCoordinates: Boolean = false) {
        if (!mutableSending.compareAndSet(false, true)) return
        mutableError.value = null
        val appContext = context.applicationContext
        scope.launch {
            try {
                val store = ActivityStore.get(appContext)
                val prepared = if (requireCoordinates) {
                    check(LocationBeacon.requested(appContext)) { "Location sharing was turned off." }
                    check(LocationCapture.permitted(appContext) && LocationCapture.enabled(appContext)) {
                        "Phone location is unavailable."
                    }
                    record.copy(coordinates = LocationCapture.fresh(appContext))
                } else record
                // A durable local copy is required before any network request.
                if (!store.insert(prepared)) return@launch
                val url = RelaySettings.url()
                if (url.isBlank()) { store.updateStatus(prepared.id, "local"); return@launch }
                try {
                    check(!requireCoordinates || LocationBeacon.requested(appContext)) {
                        "Location sharing was turned off before sending."
                    }
                    val client = RelayClient.app(appContext)
                    client.register(DeviceIdentity.displayName(appContext))
                    val result = client.publish(prepared, ReceiverStatus.session, recipientIds)
                    store.recordAcceptance(prepared.id, result)
                    ReceiptCoordinator.track(appContext, prepared.id)
                } catch (_: Exception) {
                    // A timeout could follow acceptance. Never queue or rebroadcast silently.
                    store.updateStatus(prepared.id, "uncertain")
                }
            } catch (_: Exception) {
                mutableError.value = if (requireCoordinates) {
                    "Could not get a fresh location fix. The update was not sent."
                } else "Could not save this activity on the phone. Check available storage."
            } finally { mutableSending.value = false }
        }
    }
}
