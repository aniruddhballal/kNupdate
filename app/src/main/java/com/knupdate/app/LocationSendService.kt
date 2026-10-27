package com.knupdate.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException

/** One explicit, user-started snapshot. Waiting does not subscribe to location updates. */
class LocationSendService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) { stopSelf(); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        if (intent == null || !intent.hasExtra("id")) { stopSelf(); return START_NOT_STICKY }
        val record = ActivityRecord(id = intent.getStringExtra("id")!!, title = intent.getStringExtra("title")!!,
            body = intent.getStringExtra("body")!!, startAt = intent.getLongExtra("at", 0), endAt = null,
            timeZone = intent.getStringExtra("zone")!!, status = "scheduled",
            deliveryMode = intent.getStringExtra("deliveryMode") ?: DeliveryMode.LEAF.wireValue,
            recipientIds = intent.getStringArrayListExtra("recipientIds"))
        try {
            check(LocationCapture.permitted(this) && LocationCapture.enabled(this))
            showNotification("Location scheduled", record)
        } catch (_: Exception) {
            mutableError.value = "Could not start location sharing. Check location permission and phone location."
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        current = this
        mutableActive.value = true
        scope.launch {
            val store = ActivityStore.get(this@LocationSendService)
            var saved = false
            var completed = false
            var posting = false
            try {
                saved = withContext(Dispatchers.IO) { store.insert(record) }
                check(saved)
                val alarm = getSystemService(AlarmManager::class.java)
                if (record.startAt > System.currentTimeMillis()) {
                    // Checked in the visible activity as well; never silently fall back to an inexact alarm.
                    check(Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) { "Alarm access is required." }
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, record.startAt, alarmIntent(this@LocationSendService))
                    while (record.startAt > System.currentTimeMillis()) {
                        delay((record.startAt - System.currentTimeMillis()).coerceIn(1, 1_000))
                    }
                }
                holdWakeLock()
                showNotification("Getting current coordinates", record)
                withContext(Dispatchers.IO) { store.updateStatus(record.id, "locating") }
                val fix = try { LocationCapture.fresh(this@LocationSendService) }
                    catch (_: TimeoutCancellationException) { error("No fresh location fix was available.") }
                check(fix.capturedAt >= record.startAt)
                // Commit coordinates locally before attempting the one and only POST.
                withContext(Dispatchers.IO) { store.capture(record.id, fix) }
                posting = true
                val client = RelayClient.app(this@LocationSendService)
                client.register(DeviceIdentity.displayName(this@LocationSendService))
                val result = client.publish(record.copy(coordinates = fix), ReceiverStatus.session,
                    record.deliveryMode, record.recipientIds)
                withContext(Dispatchers.IO) { store.recordAcceptance(record.id, result) }
                ReceiptCoordinator.track(this@LocationSendService, record.id)
                completed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = if (posting) "Location send unconfirmed. It will not retry automatically."
                    else "Location was not sent. Check phone location, location permission, and Alarms & reminders for scheduled sends."
                withContext(Dispatchers.IO) { if (saved) store.updateStatus(record.id, if (posting) "uncertain" else "location_failed") }
                completed = true
            } finally {
                withContext(NonCancellable) {
                    try {
                        withContext(Dispatchers.IO) {
                            // Also covers cancellation during a SQLite insert whose commit has already completed.
                            if (!completed) store.updateStatus(record.id, if (posting) "uncertain" else "cancelled")
                        }
                    } finally {
                        getSystemService(AlarmManager::class.java).cancel(alarmIntent(this@LocationSendService))
                        wakeLock?.let { if (it.isHeld) it.release() }
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun holdWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "knupdate:location-send").apply { acquire(90_000) }
    }

    internal fun wake() {
        holdWakeLock()
    }

    private fun showNotification(text: String, record: ActivityRecord) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Scheduled location", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(this, 0, Intent(this, LocationSendService::class.java).setAction(CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text).setContentText(record.timeText()).setContentIntent(open).setOngoing(true)
            .addAction(0, "Cancel location send", cancel).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION, notification)
    }

    override fun onDestroy() {
        if (current === this) current = null
        scope.cancel()
        mutableActive.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "scheduled_location"
        private const val NOTIFICATION = 2001
        private const val CANCEL = "com.knupdate.app.CANCEL_LOCATION"
        private val mutableActive = MutableStateFlow(false)
        val active = mutableActive.asStateFlow()
        private val mutableError = MutableStateFlow<String?>(null)
        val error = mutableError.asStateFlow()
        internal var current: LocationSendService? = null
            private set

        fun schedule(context: Context, record: ActivityRecord): Boolean {
            if (!mutableActive.compareAndSet(false, true)) return false
            mutableError.value = null
            try {
                ContextCompat.startForegroundService(context, Intent(context, LocationSendService::class.java)
                    .putExtra("id", record.id).putExtra("title", record.title).putExtra("body", record.body)
                    .putExtra("at", record.startAt).putExtra("zone", record.timeZone)
                    .putExtra("deliveryMode", record.deliveryMode)
                    .putStringArrayListExtra("recipientIds", ArrayList(record.recipientIds.orEmpty())))
            } catch (error: Exception) { mutableActive.value = false; throw error }
            return true
        }

        fun cancel(context: Context) { context.stopService(Intent(context, LocationSendService::class.java)) }

        private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(context, 0,
            Intent(context, LocationSendAlarm::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Never restarts location access from the background after the user-started service has ended. */
class LocationSendAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { LocationSendService.current?.wake() }
}
