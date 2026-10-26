package com.knupdate.app

import android.os.Bundle
import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
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
            .clip(CircleShape).background(trackColor)
            .border(1.dp, Ink, CircleShape)) {
            Box(modifier = Modifier.offset(x = 4.dp + (32.dp - thumbWidth) * position.coerceIn(0f, 1f), y = 4.dp)
                .size(width = thumbWidth, height = 16.dp)
                .background(thumbColor, CircleShape).border(0.8.dp, Ink, CircleShape))
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
    var tab by rememberSaveable { mutableStateOf("New") }
    var receiverError by rememberSaveable { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val defaultTime = remember { ActivityTime.now() }
    var selectedActivityId by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().id) }
    var selectedGroupId by rememberSaveable { mutableStateOf(ACTIVITY_GROUPS.first().id) }
    var selectedBody by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().bodies.first()) }
    var startAt by rememberSaveable { mutableStateOf(defaultTime) }
    var endAt by rememberSaveable { mutableStateOf(defaultTime) }
    var isRange by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().isRange) }
    var timeError by rememberSaveable { mutableStateOf<String?>(null) }
    var updateButtonSuccess by rememberSaveable { mutableStateOf(false) }
    var lastSentId by rememberSaveable { mutableStateOf<String?>(null) }
    var timePickerIsStart by remember { mutableStateOf<Boolean?>(null) }
    val selectedActivity = ACTIVITY_OPTIONS.firstOrNull { it.id == selectedActivityId } ?: ACTIVITY_OPTIONS.first()
    val selectedGroup = ACTIVITY_GROUPS.firstOrNull { it.id == selectedGroupId } ?: ACTIVITY_GROUPS.first()
    val lastSent = updates.firstOrNull { it.id == lastSentId }

    fun selectActivity(id: String) {
        if (id == selectedActivityId) return
        val activity = ACTIVITY_OPTIONS.first { it.id == id }
        selectedActivityId = id
        selectedBody = activity.bodies.first()
        isRange = activity.isRange
        startAt = ActivityTime.now()
        endAt = startAt
        timeError = null
    }

    fun startReceiving() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ReceiverService::class.java))
        } catch (_: Exception) { receiverError = "Could not start receiving." }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startReceiving()
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
        if (!ready || sending) return
        if (RelaySettings.url().isBlank()) { receiverError = "Unavailable"; return }
        val selectedEnd = if (isRange) endAt else null
        if (selectedBody !in selectedActivity.bodies) return
        timeError = ActivityTime.error(startAt, selectedEnd, ActivityTime.now())
        if (timeError != null) return
        val update = ActivityRecord(title = selectedActivity.title, body = selectedBody,
            startAt = startAt, endAt = selectedEnd)
        lastSentId = update.id
        timeError = null
        SendCoordinator.send(context, update)
    }

    Column(modifier = Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader { ReceivingToggle(receiver, ::changeReceiving) }
        IconNavigation(selected = tab, onSelect = { tab = it })
        loadError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
        receiverError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
        when (tab) {
            "Sent", "Inbox" -> HistoryList(updates.filter { it.direction == if (tab == "Sent") "sent" else "received" })
            else -> Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("ACTIVITY")
                ActivityGroupStrip(selectedGroup.id) { id ->
                    if (id != selectedGroupId) {
                        selectedGroupId = id
                        selectActivity(ACTIVITY_GROUPS.first { it.id == id }.activities.first().id)
                    }
                }
                ActivityGrid(selectedGroup.activities, selectedActivityId, ::selectActivity)
                Text(selectedActivity.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                SectionLabel("DETAIL")
                ChoiceList(selectedActivity.bodies, selectedBody) { selectedBody = it }
                SectionLabel("TIME")
                TimeModeSelector(isRange) { isRange = it; timeError = null }
                TimestampField(if (isRange) "From" else "At", startAt,
                    onEdit = { timePickerIsStart = true },
                    onNow = { startAt = ActivityTime.now(); timeError = null })
                if (isRange) TimestampField("To", endAt,
                    onEdit = { timePickerIsStart = false },
                    onNow = { endAt = ActivityTime.now(); timeError = null })
                timeError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
                if (ready && !sending) UpdateButton(updateButtonSuccess, ::saveUpdate, Modifier.fillMaxWidth())
                else BlackButton(if (sending) "SENDING" else "LOADING", enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth())
                lastSent?.let { Text(deliveryText(it), color = Muted, fontSize = 13.sp) }
                sendError?.let { Text("Could not save.", color = Color(0xFFB3261E), fontSize = 13.sp) }
            }
        }
    }
    timePickerIsStart?.let { isStart ->
        val zone = ZoneId.systemDefault()
        val timestamp = if (isStart) startAt else endAt
        val selectedDate = ActivityTime.date(timestamp, zone)
        KNTimePickerDialog(ActivityTime.minutes(timestamp, zone), selectedDate,
            onDismiss = { timePickerIsStart = null },
            onConfirm = { chosen ->
                val manualTime = ActivityTime.manual(selectedDate, chosen, zone)
                if (isStart) startAt = manualTime else endAt = manualTime
                timeError = null; timePickerIsStart = null
            })
    }
}

private fun deliveryText(record: ActivityRecord): String = when (record.status) {
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
                Text(record.timeText(), color = Muted, fontSize = 12.sp)
                if (record.direction == "sent") Text(deliveryText(record), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun KNTimePickerDialog(
    initialMinutes: Int,
    date: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val nowLimit = remember { if (date < LocalDate.now()) 1439 else currentTimeMinutes() }
    val maxHour = nowLimit / 60
    val maxMinute = nowLimit % 60
    val safeInitial = initialMinutes.coerceIn(0, nowLimit)
    var selectedHour by remember { mutableStateOf(safeInitial / 60) }
    var selectedMinute by remember { mutableStateOf(safeInitial % 60) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            color = Paper,
            shape = RoundedCornerShape(0.dp),
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
                        shape = RoundedCornerShape(0.dp),
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
                .border(1.dp, Rule, RoundedCornerShape(0.dp)),
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
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ACTIVITY_GROUPS.forEach { group ->
            val active = selected == group.id
            val interaction = remember { MutableInteractionSource() }
            Column(Modifier.width(78.dp).clip(RoundedCornerShape(10.dp))
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
private fun ActivityGrid(options: List<ActivityOption>, selectedActivityId: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val active = option.id == selectedActivityId
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.94f else 1f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f), label = "Activity press")
                    val shape = RoundedCornerShape(12.dp)
                    Column(modifier = Modifier.weight(1f).height(92.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(shape).background(if (active) Ink else Paper).border(1.dp, if (active) Ink else Rule, shape)
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
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TimeModeSelector(isRange: Boolean, onSelect: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "At", true to "From–to").forEach { (range, label) ->
            val active = isRange == range
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .background(if (active) Ink else Paper)
                .border(1.dp, if (active) Ink else Rule, RoundedCornerShape(8.dp))
                .selectable(active, interactionSource = interaction, indication = null,
                    role = Role.RadioButton, onClick = { onSelect(range) }).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (active) Paper else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TimestampField(label: String, timestamp: Long, onEdit: () -> Unit, onNow: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val includeDate = ActivityTime.date(timestamp, zone) != LocalDate.now(zone)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeButton("$label  ${ActivityTime.text(timestamp, zone, includeDate)}", onEdit,
            Modifier.weight(1f).semantics { contentDescription = "$label selected time" })
        TimeButton("Now", onNow, Modifier.width(72.dp).semantics { contentDescription = "$label current time" })
    }
}

@Composable
private fun ChoiceList(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { choice ->
                    val active = choice == selected
                    val interaction = remember { MutableInteractionSource() }
                    val shape = RoundedCornerShape(8.dp)
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                        .background(if (active) Ink else Paper).border(1.dp, if (active) Ink else Rule, shape)
                        .selectable(active, interactionSource = interaction, indication = null,
                            role = Role.RadioButton, onClick = { onSelect(choice) }).padding(12.dp),
                        contentAlignment = Alignment.Center) {
                        Text(choice, color = if (active) Paper else Ink, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TimeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(0.dp),
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
        shape = RoundedCornerShape(0.dp),
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
            .border(1.dp, Ink, RoundedCornerShape(0.dp)),
        shape = RoundedCornerShape(0.dp),
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
            .border(1.dp, Ink, RoundedCornerShape(0.dp))
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
