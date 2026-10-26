package com.knupdate.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class ReceiverState(val running: Boolean = false, val message: String = "Receiving is off")

object ReceiverStatus {
    internal val mutable = MutableStateFlow(ReceiverState())
    val state = mutable.asStateFlow()
    @Volatile var session: String? = null
        internal set
}

class ReceiverService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: RelayClient
    private lateinit var connectivity: ConnectivityManager
    @Volatile private var started = false
    private var lastMessage = ""
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (started) {
                val oldSession = ReceiverStatus.session
                ReceiverStatus.session = UUID.randomUUID().toString()
                client.cancelPoll()
                if (oldSession != null) scope.launch { runCatching { client.close(oldSession) } }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        val url = RelaySettings.url(this)
        if (url.isBlank()) { stopSelf(); return START_NOT_STICKY }
        client = RelayClient(url)
        ReceiverStatus.session = UUID.randomUUID().toString()
        val notification = serviceNotification("Connecting to relay")
        if (Build.VERSION.SDK_INT >= 29) startForeground(SERVICE_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(SERVICE_NOTIFICATION, notification)
        started = true
        ReceiverStatus.mutable.value = ReceiverState(true, "Connecting to relay")
        scope.launch {
            var backoff = 1_000L
            val store = ActivityStore.get(this@ReceiverService)
            try {
                while (isActive) {
                    if (!hasInternet()) {
                        setStatus("Offline - updates sent now will be missed")
                        delay(2_000)
                        continue
                    }
                    try {
                        val session = ReceiverStatus.session ?: break
                        setStatus("Listening for public kNupdates")
                        val updates = client.poll(session)
                        if (session != ReceiverStatus.session) continue
                        val acknowledgements = mutableListOf<String>()
                        for (index in 0 until updates.length()) {
                            val wire = updates.getJSONObject(index)
                            if (wire.optLong("expiresAt", 0) <= System.currentTimeMillis()) continue
                            val record = ActivityRecord.fromWire(wire)
                            // SQLite commit completes before ACK; replay after a lost ACK is deduplicated.
                            val inserted = store.insert(record)
                            acknowledgements.add(record.id)
                            if (inserted) runCatching { notifyActivity(record) }
                        }
                        if (acknowledgements.isNotEmpty()) client.acknowledge(session, acknowledgements)
                        backoff = 1_000
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        setStatus(if (error is RelayAccessException) "Relay hosting requires browser verification. Check Relay settings."
                            else "Relay unavailable - retrying")
                        delay(backoff)
                        backoff = (backoff * 2).coerceAtMost(30_000)
                    }
                }
            } catch (_: CancellationException) { /* Service explicitly stopped. */ }
        }
        return START_NOT_STICKY
    }

    private fun hasInternet(): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun setStatus(message: String) {
        ReceiverStatus.mutable.value = ReceiverState(true, message)
        if (lastMessage != message) {
            lastMessage = message
            getSystemService(NotificationManager::class.java).notify(SERVICE_NOTIFICATION, serviceNotification(message))
        }
    }

    private fun serviceNotification(message: String) = NotificationCompat.Builder(this, LISTEN_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("kNupdate receiving")
        .setContentText(message).setContentIntent(openApp(this)).setOngoing(true).setOnlyAlertOnce(true)
        .addAction(0, "Stop receiving", PendingIntent.getService(this, 2,
            Intent(this, ReceiverService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun notifyActivity(record: ActivityRecord) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(record.id.hashCode(),
            NotificationCompat.Builder(this, UPDATE_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(record.title).setContentText(record.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${record.body}\n${record.timeText()}"))
                .setContentIntent(openApp(this)).setAutoCancel(true).build())
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        ReceiverStatus.mutable.value = ReceiverState(false, "Android paused receiving. Open the app and start it again.")
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        val session = ReceiverStatus.session
        ReceiverStatus.session = null
        if (::client.isInitialized) {
            // Socket cleanup must not delay Android's foreground-service timeout on the main thread.
            CoroutineScope(Dispatchers.IO).launch {
                client.cancelPoll()
                if (session != null) runCatching { client.close(session) }
            }
        }
        scope.cancel()
        connectivity.unregisterNetworkCallback(networkCallback)
        if (ReceiverStatus.mutable.value.running) ReceiverStatus.mutable.value = ReceiverState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SERVICE_NOTIFICATION = 1
        private const val LISTEN_CHANNEL = "receiving"
        private const val UPDATE_CHANNEL = "updates"
        private const val ACTION_STOP = "com.knupdate.STOP_RECEIVING"

        fun createChannels(context: Context) {
            context.getSystemService(NotificationManager::class.java).apply {
                createNotificationChannel(NotificationChannel(LISTEN_CHANNEL, "Background receiving", NotificationManager.IMPORTANCE_LOW))
                createNotificationChannel(NotificationChannel(UPDATE_CHANNEL, "New kNupdates", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }

        private fun openApp(context: Context) = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).putExtra("show_inbox", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
