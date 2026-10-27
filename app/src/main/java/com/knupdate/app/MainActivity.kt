package com.knupdate.app

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.app.AlarmManager
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.knupdate.app.ui.theme.KNupdateTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Muted = Color(0xFF555555)
private val Rule = Color(0xFFDDDDDD)
private val ActiveRow = Color(0xFFF2F2F2)
private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val LiveTimeFormatter = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)

class MainActivity : ComponentActivity() {
    private val inboxRequested = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inboxRequested.value = intent.getBooleanExtra("show_inbox", false)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            KNupdateTheme {
                KNupdateApp(inboxRequested.value) { inboxRequested.value = false }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inboxRequested.value = intent.getBooleanExtra("show_inbox", false)
    }
}

@Composable
private fun KNupdateApp(showInbox: Boolean, onInboxOpened: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("knupdate_local", 0)
    }
    var displayName by rememberSaveable {
        mutableStateOf(preferences.getString(DISPLAY_NAME_KEY, "").orEmpty())
    }

    if (displayName.isBlank()) {
        NameSetupScreen(
            onSave = { name ->
                val normalized = name.trim()
                preferences.edit().putString(DISPLAY_NAME_KEY, normalized).apply()
                displayName = normalized
            }
        )
    } else {
        LocalActivityScreen(showInbox, onInboxOpened)
    }
}

@Composable
private fun ScreenHeader(trailing: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Ink,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "kN",
                    color = Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.alignByBaseline()
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun ReceivingToggle(receiver: ReceiverState, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val position by animateFloatAsState(
        targetValue = if (receiver.running) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 450f), label = "Receiving position")
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed) 20.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f), label = "Receiving thumb press")
    val trackColor by animateColorAsState(
        targetValue = if (receiver.running) Ink else Paper,
        animationSpec = tween(180), label = "Receiving track")
    val thumbColor by animateColorAsState(
        targetValue = if (receiver.running) Paper else Ink,
        animationSpec = tween(180), label = "Receiving thumb")
    Box(modifier = Modifier.size(width = 52.dp, height = 48.dp)
        .semantics {
            contentDescription = "Receive updates"
            stateDescription = receiver.label
        }
        .toggleable(value = receiver.running, enabled = receiver.running || RelaySettings.url().isNotBlank(),
            interactionSource = interaction, indication = null,
            role = Role.Switch, onValueChange = onChange), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(width = 40.dp, height = 24.dp)
            .background(trackColor)
            .border(1.dp, Ink)) {
            Box(modifier = Modifier.offset(x = 4.dp + (32.dp - thumbWidth) * position.coerceIn(0f, 1f), y = 4.dp)
                .size(width = thumbWidth, height = 16.dp)
                .background(thumbColor).border(0.8.dp, Ink))
        }
    }
}

@Composable
private fun IconNavigation(selected: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("New", "New activity", R.drawable.ic_add),
        Triple("Sent", "Sent activities", R.drawable.ic_sent),
        Triple("Inbox", "Inbox", R.drawable.ic_postbox)
    )
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { (key, description, icon) ->
            val active = selected == key
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) 0.88f else 1f,
                animationSpec = if (pressed) tween(70) else spring(dampingRatio = 0.7f, stiffness = 700f),
                label = "Navigation icon press")
            val pressAlpha by animateFloatAsState(
                targetValue = if (pressed) 0.65f else 1f,
                animationSpec = tween(100), label = "Navigation icon opacity")
            Box(modifier = Modifier.size(48.dp).background(if (active) Ink else Paper)
                .semantics { contentDescription = description }
                .selectable(selected = active, interactionSource = interaction, indication = null,
                    role = Role.Tab, onClick = { onSelect(key) }),
                contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(icon), contentDescription = null,
                    tint = if (active) Paper else Ink, modifier = Modifier.size(26.dp).graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                        alpha = pressAlpha
                    })
            }
        }
    }
}

@Composable
private fun NameSetupScreen(onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        ScreenHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Box(modifier = Modifier.size(width = 42.dp, height = 4.dp).background(Ink))
            Text(
                text = "Set your name",
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            LocalTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Display name",
                onDone = { if (name.trim().isNotEmpty()) onSave(name) }
            )
            BlackButton(
                label = "SAVE NAME",
                enabled = name.trim().isNotEmpty(),
                onClick = { onSave(name) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LocalActivityScreen(showInbox: Boolean, onInboxOpened: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ActivityStore.get(context) }
    val updates by store.records.collectAsState()
    val receiver by ReceiverStatus.state.collectAsState()
    val sending by SendCoordinator.sending.collectAsState()
    val sendError by SendCoordinator.error.collectAsState()
    val locationActive by LocationSendService.active.collectAsState()
    val locationError by LocationSendService.error.collectAsState()
    var tab by rememberSaveable { mutableStateOf("New") }
    var receiverError by rememberSaveable { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedActivityId by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().id) }
    var selectedGroupId by rememberSaveable { mutableStateOf(ACTIVITY_GROUPS.first().id) }
    var selectedBody by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().bodies.first()) }
    var customStartAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEndAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var isRange by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().defaultIsRange) }
    var timeError by rememberSaveable { mutableStateOf<String?>(null) }
    var updateButtonSuccess by rememberSaveable { mutableStateOf(false) }
    var lastSentId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomTime by rememberSaveable { mutableStateOf(false) }
    var shareCoordinates by rememberSaveable { mutableStateOf(false) }
    val selectedActivity = ACTIVITY_OPTIONS.firstOrNull { it.id == selectedActivityId } ?: ACTIVITY_OPTIONS.first()
    val selectedGroup = ACTIVITY_GROUPS.firstOrNull { it.id == selectedGroupId } ?: ACTIVITY_GROUPS.first()
    val coordinateMode = selectedActivityId == "location" && shareCoordinates
    val lastSent = updates.firstOrNull { it.id == lastSentId }

    fun selectActivity(id: String) {
        if (id == selectedActivityId) return
        val activity = ACTIVITY_OPTIONS.first { it.id == id }
        selectedActivityId = id
        selectedBody = activity.bodies.first()
        isRange = activity.defaultIsRange
        customStartAt = null
        customEndAt = null
        timeError = null
        shareCoordinates = false
    }

    fun startReceiving() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ReceiverService::class.java))
        } catch (_: Exception) { receiverError = "Could not start receiving." }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startReceiving()
    }
    val locationNotificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (LocationCapture.permitted(context)) {
            shareCoordinates = true
            timeError = null
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                locationNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else timeError = "Allow location in Android app settings to share coordinates."
    }

    fun changeReceiving(enabled: Boolean) {
        receiverError = null
        if (!enabled) ReceiverService.stop(context)
        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startReceiving()
    }

    LaunchedEffect(Unit) {
        if (tab !in listOf("New", "Sent", "Inbox")) tab = "New"
        try { withContext(Dispatchers.IO) { store.load() }; ready = true }
        catch (_: Exception) { loadError = "History unavailable." }
    }
    LaunchedEffect(showInbox) {
        if (showInbox) { tab = "Inbox"; onInboxOpened() }
    }
    LaunchedEffect(selectedActivityId) {
        if (selectedBody !in selectedActivity.bodies) selectedBody = selectedActivity.bodies.first()
    }
    LaunchedEffect(lastSent?.status) {
        if (lastSent?.status == "sent") { updateButtonSuccess = true; delay(850); updateButtonSuccess = false }
    }

    fun saveUpdate() {
        val pressedAt = ActivityTime.now()
        if (!ready || sending) return
        if (RelaySettings.url().isBlank()) { receiverError = "Unavailable"; return }
        if (selectedBody !in selectedActivity.bodies) return
        timeError = ActivityTime.selectionError(selectedActivity.timePolicy, customStartAt, customEndAt, pressedAt, coordinateMode)
        if (timeError != null) return
        val selectedTime = ActivityTime.resolve(customStartAt, customEndAt, pressedAt)
        val live = customStartAt == null
        if (coordinateMode) {
            if (locationActive) { timeError = "A location send is already pending. Cancel it before scheduling another."; return }
            if (!LocationCapture.permitted(context)) {
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return
            }
            if (!LocationCapture.enabled(context)) { timeError = "Turn on your phone's location, then tap kNupdate again."; return }
            val at = selectedTime.startAt
            if (!live && Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                timeError = "Allow Alarms & reminders, then return and tap kNupdate again. Or use Live to send immediately."
                try { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                catch (_: Exception) { timeError = "Enable Alarms & reminders in Android app settings, or use Live." }
                return
            }
            val update = ActivityRecord(title = selectedActivity.title, body = selectedBody, startAt = at, endAt = null, status = "scheduled")
            try {
                if (LocationSendService.schedule(context, update)) { lastSentId = update.id; timeError = null }
            } catch (_: Exception) { timeError = "Could not start location sharing. Keep the app open and check location permission." }
            return
        }
        val update = ActivityRecord(title = selectedActivity.title, body = selectedBody,
            startAt = selectedTime.startAt, endAt = selectedTime.endAt)
        lastSentId = update.id
        timeError = null
        SendCoordinator.send(context, update)
    }

    Column(modifier = Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader { ReceivingToggle(receiver, ::changeReceiving) }
        IconNavigation(selected = tab, onSelect = { tab = it })
        loadError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
        receiverError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
        if (locationActive) {
            Text("Location send pending", color = Muted, fontSize = 13.sp)
            OutlinedButton(onClick = { LocationSendService.cancel(context) }, shape = RectangleShape) { Text("Cancel location send", color = Ink) }
        }
        locationError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
        when (tab) {
            "Sent", "Inbox" -> HistoryList(updates.filter { it.direction == if (tab == "Sent") "sent" else "received" })
            else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).testTag("activity-composer"),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "activity-label", contentType = "section") { SectionLabel("ACTIVITY") }
                item(key = "groups", contentType = "groups") {
                    ActivityGroupStrip(selectedGroup.id) { id ->
                        if (id != selectedGroupId) {
                            selectedGroupId = id
                            selectActivity(ACTIVITY_GROUPS.first { it.id == id }.activities.first().id)
                        }
                    }
                }
                items(selectedGroup.activities.chunked(4), key = { "activities-${it.first().id}" }, contentType = { "activity-row" }) { row ->
                    ActivityGridRow(row, selectedActivityId, ::selectActivity)
                }
                item(key = "selected-title", contentType = "title") {
                    Text(selectedActivity.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                if (selectedActivityId == "location") item(key = "share-coordinates", contentType = "location") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceRow(listOf("Description only", "Share coordinates"), if (coordinateMode) "Share coordinates" else "Description only") { choice ->
                            timeError = null
                            customStartAt = null; customEndAt = null
                            if (choice == "Description only") shareCoordinates = false
                            else if (LocationCapture.permitted(context)) {
                                shareCoordinates = true
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                    locationNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                        if (coordinateMode) Text("One fresh snapshot when you tap kNupdate. Use Custom to schedule a future time. Scheduled sends need Alarms & reminders access. Keep phone location on.", color = Muted, fontSize = 12.sp)
                    }
                }
                item(key = "detail-label", contentType = "section") { SectionLabel("DETAIL") }
                items(selectedActivity.bodies.chunked(2), key = { "detail-${selectedActivity.id}-${it.first()}" }, contentType = { "detail-row" }) { row ->
                    ChoiceRow(row, selectedBody) { selectedBody = it }
                }
                item(key = "time-label", contentType = "section") { SectionLabel("TIME") }
                item(key = "time-controls", contentType = "time") {
                    ActivityTimeControls(customStartAt, customEndAt,
                        onCustom = { showCustomTime = true },
                        onLive = { customStartAt = null; customEndAt = null; timeError = null })
                }
                timeError?.let { error -> item(key = "time-error", contentType = "feedback") { Text(error, color = Color(0xFFB3261E), fontSize = 13.sp) } }
                item(key = "send", contentType = "send") {
                    if (ready && !sending) UpdateButton(updateButtonSuccess, ::saveUpdate, Modifier.fillMaxWidth())
                    else BlackButton(if (sending) "SENDING" else "LOADING", enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth())
                }
                lastSent?.let { record -> item(key = "delivery", contentType = "feedback") { Text(deliveryText(record), color = Muted, fontSize = 13.sp) } }
                sendError?.let { item(key = "send-error", contentType = "feedback") { Text("Could not save.", color = Color(0xFFB3261E), fontSize = 13.sp) } }
            }
        }
    }
    if (showCustomTime) {
        CustomTimeDialog(customStartAt, customEndAt, isRange, selectedActivity.timePolicy, coordinateMode,
            onDismiss = { showCustomTime = false },
            onConfirm = { start, end, range ->
                customStartAt = start; customEndAt = end; isRange = range
                timeError = null; showCustomTime = false
            })
    }
}

private fun deliveryText(record: ActivityRecord): String = when (record.status) {
    "scheduled" -> "Location scheduled"
    "locating" -> "Getting coordinates"
    "location_failed" -> "Location unavailable · not sent"
    "cancelled" -> "Cancelled · not sent"
    "missed" -> "Location send interrupted · not sent"
    "sending" -> "Sending"
    "sent" -> if (record.listeners == 0) "No listeners" else "Sent · ${record.listeners} listening"
    "uncertain" -> "Unconfirmed"
    "received" -> "Received"
    else -> "Local"
}

@Composable
private fun HistoryList(records: List<ActivityRecord>) {
    if (records.isEmpty()) {
        Text("No updates",
            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
    } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(records, key = { it.id }) { record ->
            Column(modifier = Modifier.fillMaxWidth().border(1.dp, Rule).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(record.body, color = Ink, fontSize = 15.sp)
                record.coordinates?.let { fix ->
                    Text(fix.text(), color = Ink, fontSize = 14.sp)
                    Text("Captured ${ActivityTime.text(fix.capturedAt, ZoneId.of(record.timeZone), includeDate = true)}", color = Muted, fontSize = 12.sp)
                }
                Text(record.timeText(), color = Muted, fontSize = 12.sp)
                if (record.direction == "sent") Text(deliveryText(record), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CustomTimeDialog(
    initialStart: Long?,
    initialEnd: Long?,
    initialIsRange: Boolean,
    timePolicy: ActivityTimePolicy,
    coordinates: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long?, Boolean) -> Unit
) {
    var start by rememberSaveable { mutableStateOf(initialStart) }
    var end by rememberSaveable { mutableStateOf(initialEnd) }
    var preferredRange by rememberSaveable { mutableStateOf(initialIsRange) }
    val policy = if (coordinates) ActivityTimePolicy.AT_ONLY else timePolicy
    // Constrain even restored drafts from a version that allowed every mode.
    val range = policy.rangeSelection(preferredRange)
    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var pickerReference by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun edit(isStart: Boolean) {
        pickerReference = (if (isStart) start else end) ?: ActivityTime.now()
        editingStart = isStart
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("custom-time-dialog"),
            color = Paper, shape = RectangleShape, border = BorderStroke(1.dp, Ink)) {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("CUSTOM TIME")
                if (policy == ActivityTimePolicy.BOTH) TimeModeSelector(range) { preferredRange = it; error = null }
                else Text(if (policy == ActivityTimePolicy.AT_ONLY) "At only" else "From–to only", color = Muted, fontSize = 12.sp)
                TimestampField(if (range) "From" else "At", start, onEdit = { edit(true) })
                if (range) TimestampField("To", end, onEdit = { edit(false) })
                error?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeButton("CANCEL", onDismiss, Modifier.weight(1f))
                    BlackButton("USE TIME", enabled = start != null && (!range || end != null),
                        onClick = {
                            val selectedStart = start ?: return@BlackButton
                            val selectedEnd = if (range) end else null
                            error = ActivityTime.selectionError(policy, selectedStart, selectedEnd, ActivityTime.now(), coordinates)
                            if (error == null) onConfirm(selectedStart, selectedEnd, range)
                        }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    editingStart?.let { isStart ->
        val timestamp = pickerReference ?: ActivityTime.now()
        val zone = ZoneId.systemDefault()
        val date = ActivityTime.date(timestamp, zone)
        KNTimePickerDialog(ActivityTime.minutes(timestamp, zone), date, allowFuture = coordinates,
            onDismiss = { editingStart = null },
            onConfirm = { minutes ->
                val selected = ActivityTime.manual(date, minutes, zone)
                if (isStart) start = selected else end = selected
                error = null; editingStart = null
            })
    }
}

@Composable
private fun KNTimePickerDialog(
    initialMinutes: Int,
    date: LocalDate,
    allowFuture: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val nowLimit = remember { if (allowFuture || date < LocalDate.now()) 1439 else currentTimeMinutes() }
    val maxHour = nowLimit / 60
    val maxMinute = nowLimit % 60
    val safeInitial = initialMinutes.coerceIn(0, nowLimit)
    var selectedHour by rememberSaveable { mutableStateOf(safeInitial / 60) }
    var selectedMinute by rememberSaveable { mutableStateOf(safeInitial % 60) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp).testTag("time-picker-dialog"),
            color = Paper,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Ink)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (date == LocalDate.now()) "TIME TODAY" else date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)).uppercase(),
                    color = Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = formatMinutes(selectedHour * 60 + selectedMinute),
                    color = Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimeValuePicker(
                        label = "HOUR",
                        values = 0..23,
                        selected = selectedHour,
                        enabled = { it <= maxHour },
                        onSelect = { hour ->
                            selectedHour = hour
                            if (hour == maxHour && selectedMinute > maxMinute) {
                                selectedMinute = maxMinute
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TimeValuePicker(
                        label = "MINUTE",
                        values = 0..59,
                        selected = selectedMinute,
                        enabled = { selectedHour < maxHour || it <= maxMinute },
                        onSelect = { selectedMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                        border = BorderStroke(1.dp, Ink)
                    ) {
                        Text(text = "CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    }
                    BlackButton(
                        label = "OK",
                        onClick = { onConfirm(selectedHour * 60 + selectedMinute) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeValuePicker(
    label: String,
    values: IntRange,
    selected: Int,
    enabled: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0)
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("time-value-${label.lowercase(Locale.ENGLISH)}")
                .border(1.dp, Rule, RectangleShape),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(values.toList(), key = { it }) { value ->
                val isEnabled = enabled(value)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isEnabled) { onSelect(value) }
                        .background(if (value == selected) ActiveRow else Paper)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString().padStart(2, '0'),
                        color = when {
                            !isEnabled -> Color(0xFFCCCCCC)
                            value == selected -> Ink
                            else -> Muted
                        },
                        fontSize = 16.sp,
                        fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ActivityGroupStrip(selected: String, onSelect: (String) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag("activity-groups"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ACTIVITY_GROUPS, key = { it.id }, contentType = { "group" }) { group ->
            val active = selected == group.id
            val interaction = remember { MutableInteractionSource() }
            Column(Modifier.width(78.dp)
                .background(if (active) ActiveRow else Paper)
                .semantics { contentDescription = "${group.label} activities" }
                .selectable(active, interactionSource = interaction, indication = null,
                    role = Role.Tab, onClick = { onSelect(group.id) }).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(group.icon), null, tint = if (active) Ink else Muted, modifier = Modifier.size(22.dp))
                Text(group.label, fontSize = 10.sp, color = if (active) Ink else Muted,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ActivityGridRow(options: List<ActivityOption>, selectedActivityId: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option.id == selectedActivityId
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.94f else 1f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f), label = "Activity press")
            Column(modifier = Modifier.weight(1f).height(92.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .background(if (active) Ink else Paper).border(1.dp, if (active) Ink else Rule)
                .selectable(selected = active, interactionSource = interaction, indication = null,
                    role = Role.RadioButton, onClick = { onSelect(option.id) })
                .padding(horizontal = 2.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Icon(painterResource(option.icon), contentDescription = null,
                    tint = if (active) Paper else Ink, modifier = Modifier.size(28.dp))
                Text(option.label, color = if (active) Paper else Ink, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
        }
        repeat(4 - options.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TimeModeSelector(isRange: Boolean, onSelect: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "At", true to "From–to").forEach { (range, label) ->
            val active = isRange == range
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.weight(1f)
                .background(if (active) Ink else Paper)
                .border(1.dp, if (active) Ink else Rule, RectangleShape)
                .selectable(active, interactionSource = interaction, indication = null,
                    role = Role.RadioButton, onClick = { onSelect(range) }).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (active) Paper else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TimestampField(label: String, timestamp: Long?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val text = timestamp?.let { ActivityTime.text(it, zone, ActivityTime.date(it, zone) != LocalDate.now(zone)) } ?: "Choose time"
    TimeButton("$label  $text", onEdit, modifier.fillMaxWidth().semantics { contentDescription = "$label selected time" })
}

@Composable
private fun ActivityTimeControls(start: Long?, end: Long?, onCustom: () -> Unit, onLive: () -> Unit) {
    if (start == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveTimeField(onCustom, Modifier.weight(1f))
            TimeButton("Custom", onCustom, Modifier.width(88.dp).semantics { contentDescription = "Set custom time" })
        }
    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TimestampField(if (end == null) "At" else "From", start, onCustom, Modifier.weight(1f))
            TimeButton("Live", onLive, Modifier.width(88.dp).semantics { contentDescription = "Use live time" })
        }
        if (end != null) TimestampField("To", end, onCustom)
    }
}

/** Only this visible clock recomposes each second; its value is never used to create a send. */
@Composable
private fun LiveTimeField(onCustom: () -> Unit, modifier: Modifier = Modifier) {
    var timestamp by remember { mutableStateOf(ActivityTime.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                timestamp = ActivityTime.now()
                delay(1_000L - timestamp % 1_000L)
            }
        }
    }
    val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(LiveTimeFormatter)
    TimeButton("Live  $time", onCustom, modifier.semantics {
        contentDescription = "Live current time"
        stateDescription = "Uses the time kNupdate is pressed"
    })
}

@Composable
private fun ChoiceRow(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            val active = choice == selected
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .background(if (active) Ink else Paper).border(1.dp, if (active) Ink else Rule)
                .selectable(active, interactionSource = interaction, indication = null,
                    role = Role.RadioButton, onClick = { onSelect(choice) }).padding(12.dp),
                contentAlignment = Alignment.Center) {
                Text(choice, color = if (active) Paper else Ink, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        if (choices.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TimeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, Ink)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun BlackButton(label: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink,
            contentColor = Paper,
            disabledContainerColor = Color(0xFFDDDDDD),
            disabledContentColor = Color(0xFF777777)
        )
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun UpdateButton(success: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = !success,
        modifier = modifier
            .height(48.dp)
            .border(1.dp, Ink, RectangleShape),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink,
            contentColor = Paper,
            disabledContainerColor = Ink,
            disabledContentColor = Paper
        )
    ) {
        AnimatedContent(
            targetState = success,
            transitionSpec = {
                (slideInHorizontally(animationSpec = tween(180)) { it } + fadeIn(tween(120))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(180)) { -it } + fadeOut(tween(120)))
            },
            label = "update button content transition"
        ) { complete ->
            Text(
                text = if (complete) "✓" else "kNupdate",
                color = Paper,
                fontSize = if (complete) 15.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (complete) 0.sp else 0.8.sp
            )
        }
    }
}

@Composable
private fun LocalTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, onDone: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink, RectangleShape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) Text(text = placeholder, color = Muted, fontSize = 15.sp)
            innerTextField()
        }
    )
}

private const val DISPLAY_NAME_KEY = "display_name"

private fun currentTimeMinutes(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

private fun minutesToLocalTime(minutes: Int): LocalTime = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

private fun formatMinutes(minutes: Int): String = minutesToLocalTime(minutes).format(TimeFormatter)
