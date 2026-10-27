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
import kotlinx.coroutines.withContext
import java.util.UUID

data class ReceiverState(val running: Boolean = false, val message: String = "Receiving is off") {
    val label: String get() = when {
        !running -> if (message.startsWith("Android paused")) "Paused" else "Off"
        message.startsWith("Offline") -> "Offline"
        message.startsWith("Connecting") -> "Connecting"
        message.startsWith("Relay unavailable") -> "Retrying"
        message.startsWith("Relay hosting") -> "Unavailable"
        else -> "Listening"
    }
}

object ReceiverStatus {
    private val mutable = MutableStateFlow(ReceiverState())
    val state = mutable.asStateFlow()
    private var activeRun: String? = null
    @Volatile var session: String? = null
        private set

    @Synchronized internal fun begin(): String {
        val run = UUID.randomUUID().toString()
        activeRun = run
        session = UUID.randomUUID().toString()
        mutable.value = ReceiverState(true, "Connecting to relay")
        return run
    }

    @Synchronized internal fun isCurrent(run: String?): Boolean = run != null && run == activeRun

    @Synchronized internal fun update(run: String?, message: String): Boolean {
        if (!isCurrent(run)) return false
        mutable.value = ReceiverState(true, message)
        return true
    }

    @Synchronized internal fun rotateSession(run: String?): String? {
        if (!isCurrent(run)) return null
        val previous = session
        session = UUID.randomUUID().toString()
        return previous
    }

    @Synchronized internal fun end(run: String? = null, message: String = "Receiving is off"): String? {
        if (run != null && !isCurrent(run)) return null
        val previous = session
        activeRun = null
        session = null
        mutable.value = ReceiverState(false, message)
        return previous
    }
}

class ReceiverService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: RelayClient
    private lateinit var connectivity: ConnectivityManager
    @Volatile private var started = false
    private var runId: String? = null
    private var lastMessage = ""
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (started) {
                val oldSession = ReceiverStatus.rotateSession(runId)
                if (oldSession != null) {
                    cleanupScope.launch { client.cancelPoll() }
                    cleanupScope.launch { runCatching { client.close(oldSession) } }
                }
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
        if (intent?.action == ACTION_STOP) { stop(this); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        val url = RelaySettings.url()
        if (url.isBlank()) { stopSelf(); return START_NOT_STICKY }
        client = RelayClient.app(this)
        val run = ReceiverStatus.begin()
        runId = run
        val notification = serviceNotification("Connecting to relay")
        if (Build.VERSION.SDK_INT >= 29) startForeground(SERVICE_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(SERVICE_NOTIFICATION, notification)
        started = true
        scope.launch {
            var backoff = 1_000L
            var registered = false
            val sharedName = ListenerNames.sharedName(this@ReceiverService)
            val store = ActivityStore.get(this@ReceiverService)
            try {
                while (isActive && ReceiverStatus.isCurrent(run)) {
                    if (!hasInternet()) {
                        setStatus("Offline - updates sent now will be missed")
                        delay(2_000)
                        continue
                    }
                    try {
                        if (!registered) {
                            client.register(DeviceIdentity.displayName(this@ReceiverService))
                            registered = true
                        }
                        val session = ReceiverStatus.session ?: break
                        setStatus("Listening for kNupdates")
                        if (!ReceiverStatus.isCurrent(run)) break
                        val updates = client.poll(session, sharedName)
                        if (!ReceiverStatus.isCurrent(run) || session != ReceiverStatus.session) continue
                        val acknowledgements = mutableListOf<String>()
                        for (index in 0 until updates.length()) {
                            if (!ReceiverStatus.isCurrent(run)) break
                            val wire = updates.getJSONObject(index)
                            if (wire.optLong("expiresAt", 0) <= System.currentTimeMillis()) continue
                            val record = ActivityRecord.fromWire(wire)
                            // SQLite commit completes before ACK; replay after a lost ACK is deduplicated.
                            val inserted = store.insert(record)
                            acknowledgements.add(record.id)
                            if (inserted && ReceiverStatus.isCurrent(run)) runCatching { notifyActivity(record) }
                        }
                        if (acknowledgements.isNotEmpty() && ReceiverStatus.isCurrent(run)) client.acknowledge(session, acknowledgements)
                        backoff = 1_000
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        registered = false
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

    private suspend fun setStatus(message: String) = withContext(Dispatchers.Main.immediate) {
        // Main-thread delivery serializes notification changes with user Stop and onDestroy.
        if (!ReceiverStatus.update(runId, message)) return@withContext
        if (lastMessage != message) {
            lastMessage = message
            getSystemService(NotificationManager::class.java).notify(SERVICE_NOTIFICATION, serviceNotification(message))
        }
    }

    private fun serviceNotification(message: String) = NotificationCompat.Builder(this, LISTEN_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("kNupdate")
        .setContentText(ReceiverState(true, message).label).setContentIntent(openApp(this)).setOngoing(true).setOnlyAlertOnce(true)
        .addAction(0, "Stop receiving", PendingIntent.getService(this, 2,
            Intent(this, ReceiverService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun notifyActivity(record: ActivityRecord) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(record.id.hashCode(),
            NotificationCompat.Builder(this, UPDATE_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(record.senderName?.let { "$it · ${record.title}" } ?: record.title).setContentText(record.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(record.body,
                    record.coordinates?.text(), record.timeText()).joinToString("\n")))
                .setContentIntent(openApp(this)).setAutoCancel(true).build())
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val session = ReceiverStatus.end(runId, "Android paused receiving. Open the app and start it again.")
        if (session != null && ::client.isInitialized) cleanupScope.launch { runCatching { client.close(session) } }
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        val session = if (runId != null) ReceiverStatus.end(runId) else null
        if (::client.isInitialized) {
            // Socket cleanup must not delay Android's foreground-service timeout on the main thread.
            cleanupScope.launch { client.cancelPoll() }
            if (session != null) cleanupScope.launch { runCatching { client.close(session) } }
        }
        scope.cancel()
        connectivity.unregisterNetworkCallback(networkCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SERVICE_NOTIFICATION = 1
        private const val LISTEN_CHANNEL = "receiving"
        private const val UPDATE_CHANNEL = "updates"
        private const val ACTION_STOP = "com.knupdate.STOP_RECEIVING"
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun stop(context: Context) {
            // Stop is a local action. Invalidate callbacks before Android/network cleanup.
            val session = ReceiverStatus.end()
            val url = RelaySettings.url()
            context.stopService(Intent(context, ReceiverService::class.java))
            if (session != null && url.isNotBlank()) cleanupScope.launch {
                runCatching { RelayClient.app(context).close(session) }
            }
        }

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
