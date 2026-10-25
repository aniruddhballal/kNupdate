package com.knupdate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.knupdate.app.ui.theme.KNupdateTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Muted = Color(0xFF555555)
private val Rule = Color(0xFFDDDDDD)
private val ActiveRow = Color(0xFFF2F2F2)
private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            KNupdateTheme {
                KNupdateApp()
            }
        }
    }
}

@Composable
private fun KNupdateApp() {
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
        LocalActivityScreen()
    }
}

@Composable
private fun ScreenHeader(label: String?) {
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
        Text(
            text = "kN",
            color = Ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp
        )
        label?.let {
            Text(
                text = it,
                color = Ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
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
        ScreenHeader(label = "LOCAL ONLY")
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
            Text(
                text = "This name stays on this phone. Nothing is sent to a server.",
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 22.sp
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
private fun LocalActivityScreen() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("knupdate_local", 0)
    }
    val defaultTime = remember { currentTimeMinutes() }
    var selectedActivityId by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().id) }
    var selectedBody by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().bodies.first()) }
    var startMinutes by rememberSaveable { mutableStateOf(defaultTime) }
    var endMinutes by rememberSaveable { mutableStateOf(defaultTime) }
    var timeError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmationVisible by rememberSaveable { mutableStateOf(false) }
    var timePickerIsStart by remember { mutableStateOf<Boolean?>(null) }
    var updates by remember { mutableStateOf(loadUpdates(preferences)) }

    val selectedActivity = ACTIVITY_OPTIONS.first { it.id == selectedActivityId }

    LaunchedEffect(selectedActivityId) {
        selectedBody = selectedActivity.bodies.first()
        val now = currentTimeMinutes()
        startMinutes = now
        endMinutes = now
        timeError = null
    }

    fun chooseTime(isStart: Boolean) {
        timePickerIsStart = isStart
    }

    fun saveUpdate() {
        val now = currentTimeMinutes()
        when {
            startMinutes > now || (selectedActivity.timeMode == TimeMode.RANGE && endMinutes > now) -> {
                timeError = "Future times cannot be used."
                return
            }
            selectedActivity.timeMode == TimeMode.RANGE && endMinutes <= startMinutes -> {
                timeError = "The end time must be after the start time."
                return
            }
        }

        val update = LocalUpdate(
            title = selectedActivity.title,
            body = selectedBody,
            startMinutes = startMinutes,
            endMinutes = if (selectedActivity.timeMode == TimeMode.RANGE) endMinutes else null
        )
        updates = listOf(update) + updates
        saveUpdates(preferences, updates)
        confirmationVisible = true
        timeError = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(label = null)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel(text = "ACTIVITY")
            ActivityOptionList(
                selectedActivityId = selectedActivityId,
                onSelect = { selectedActivityId = it }
            )

            SectionLabel(text = if (selectedActivity.timeMode == TimeMode.SINGLE) "TIME" else "TIME RANGE")
            if (selectedActivity.timeMode == TimeMode.SINGLE) {
                TimeButton(
                    label = formatMinutes(startMinutes),
                    onClick = { chooseTime(isStart = true) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TimeButton(
                        label = "FROM  ${formatMinutes(startMinutes)}",
                        onClick = { chooseTime(isStart = true) },
                        modifier = Modifier.weight(1f)
                    )
                    TimeButton(
                        label = "TO  ${formatMinutes(endMinutes)}",
                        onClick = { chooseTime(isStart = false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            timeError?.let { Text(text = it, color = Color(0xFFB3261E), fontSize = 13.sp) }

            BlackButton(
                label = "kNupdate",
                onClick = ::saveUpdate,
                modifier = Modifier.fillMaxWidth()
            )

            if (confirmationVisible) {
                ConfirmationCard(onDismiss = { confirmationVisible = false })
            }

        }
    }

    timePickerIsStart?.let { isStart ->
        KNTimePickerDialog(
            initialMinutes = if (isStart) startMinutes else endMinutes,
            onDismiss = { timePickerIsStart = null },
            onConfirm = { chosen ->
                if (isStart) {
                    startMinutes = chosen
                } else {
                    endMinutes = chosen
                }
                timeError = null
                timePickerIsStart = null
            }
        )
    }
}

@Composable
private fun KNTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val nowLimit = remember { currentTimeMinutes() }
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
                    text = "TIME",
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
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        listState.animateScrollToItem((selected - 2).coerceAtLeast(0))
    }

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
private fun ActivityOptionList(selectedActivityId: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ACTIVITY_OPTIONS.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (option.id == selectedActivityId) ActiveRow else Paper)
                    .clickable { onSelect(option.id) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .background(if (option.id == selectedActivityId) Ink else Color.Transparent)
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(text = option.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Rule))
        }
    }
}

@Composable
private fun ChoiceList(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        choices.forEach { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(choice) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.dp, Ink, RoundedCornerShape(0.dp))
                        .background(if (choice == selected) Ink else Paper)
                )
                Text(text = choice, color = Ink, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Rule))
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
        border = BorderStroke(1.dp, Ink)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
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

@Composable
private fun ConfirmationCard(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ActiveRow)
            .border(1.dp, Ink, RoundedCornerShape(0.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "UPDATED", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(text = "No connections yet.", color = Ink, fontSize = 13.sp)
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(0.dp),
            border = BorderStroke(1.dp, Ink),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
        ) {
            Text(text = "DISMISS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun LocalUpdateRow(update: LocalUpdate) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Rule,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = update.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = update.body, color = Muted, fontSize = 13.sp)
        Text(text = update.timeText(), color = Muted, fontSize = 11.sp, letterSpacing = 0.4.sp)
    }
}

private enum class TimeMode { SINGLE, RANGE }

private data class ActivityOption(val id: String, val title: String, val bodies: List<String>, val timeMode: TimeMode)

private data class LocalUpdate(val title: String, val body: String, val startMinutes: Int, val endMinutes: Int?) {
    fun timeText(): String = if (endMinutes == null) formatMinutes(startMinutes) else "${formatMinutes(startMinutes)}–${formatMinutes(endMinutes)}"
}

private val ACTIVITY_OPTIONS = listOf(
    ActivityOption("location", "Current location", listOf("Home", "Work", "Away"), TimeMode.SINGLE),
    ActivityOption("working", "Working", listOf("Office", "Home", "Other"), TimeMode.RANGE),
    ActivityOption("studying", "Studying", listOf("Home", "Library", "Other"), TimeMode.RANGE),
    ActivityOption("eating", "Eating", listOf("Breakfast", "Lunch", "Dinner"), TimeMode.SINGLE),
    ActivityOption("exercising", "Exercising", listOf("Gym", "Outside", "Home"), TimeMode.RANGE),
    ActivityOption("travelling", "Travelling", listOf("Walking", "Driving", "Public transport"), TimeMode.RANGE),
    ActivityOption("sleeping", "Sleeping", listOf("Home"), TimeMode.RANGE)
)

private const val DISPLAY_NAME_KEY = "display_name"
private const val UPDATES_KEY = "updates"

private fun currentTimeMinutes(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

private fun minutesToLocalTime(minutes: Int): LocalTime = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

private fun formatMinutes(minutes: Int): String = minutesToLocalTime(minutes).format(TimeFormatter)

private fun loadUpdates(preferences: android.content.SharedPreferences): List<LocalUpdate> {
    val raw = preferences.getString(UPDATES_KEY, null) ?: return emptyList()
    return runCatching {
        val json = JSONArray(raw)
        buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                add(
                    LocalUpdate(
                        title = item.getString("title"),
                        body = item.getString("body"),
                        startMinutes = item.getInt("start"),
                        endMinutes = if (item.isNull("end")) null else item.getInt("end")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveUpdates(preferences: android.content.SharedPreferences, updates: List<LocalUpdate>) {
    val json = JSONArray()
    updates.take(50).forEach { update ->
        json.put(
            JSONObject()
                .put("title", update.title)
                .put("body", update.body)
                .put("start", update.startMinutes)
                .put("end", update.endMinutes ?: JSONObject.NULL)
        )
    }
    preferences.edit().putString(UPDATES_KEY, json.toString()).apply()
}
