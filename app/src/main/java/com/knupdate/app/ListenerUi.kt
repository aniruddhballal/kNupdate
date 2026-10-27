package com.knupdate.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
internal fun ReceivingOptionsDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("knupdate_local", 0) }
    var name by rememberSaveable { mutableStateOf(preferences.getString("display_name", "").orEmpty()) }
    var share by rememberSaveable { mutableStateOf(preferences.getBoolean("share_listener_name", false)) }
    ListenerDialog("receiving-options-dialog", onDismiss) {
        Text("Start receiving", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        LocalTextField(name, { name = it }, "Display name", onDone = {})
        ChoiceRow(listOf("Anonymous", "Share name"), if (share) "Share name" else "Anonymous") { share = it == "Share name" }
        Text(if (share) "Your display name will be public while you listen. Senders can keep it in their local listener logs after you stop."
            else "Your name stays on this phone. Others will see Anonymous listener.", fontSize = 13.sp, color = Color(0xff555555))
        if (share && !ListenerNames.valid(name)) Text("Use 1–80 characters without control characters.", fontSize = 13.sp, color = Color(0xffb3261e))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeButton("CANCEL", onDismiss, Modifier.weight(1f))
            BlackButton("START", enabled = name.trim().isNotEmpty() && (!share || ListenerNames.valid(name)),
                onClick = { onConfirm(name.trim(), share) }, modifier = Modifier.weight(1f))
        }
    }
}

private data class ActiveListener(val name: String?, val self: Boolean)

@Composable
internal fun ListenerDirectoryDialog(onDismiss: () -> Unit) {
    var listeners by remember { mutableStateOf<List<ActiveListener>>(emptyList()) }
    var asOf by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val result = RelayClient(RelaySettings.url()).listeners(ReceiverStatus.session)
                    val rows = result.getJSONArray("listeners")
                    require(rows.length() <= 100)
                    listeners = (0 until rows.length()).map { index ->
                        val item = rows.getJSONObject(index)
                        val name = if (item.isNull("name")) null else item.getString("name").also { require(ListenerNames.valid(it)) }
                        ActiveListener(name, item.getBoolean("self"))
                    }
                    asOf = result.getLong("asOf")
                    error = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    error = if (failure is RelayHttpException && failure.statusCode == 404)
                        "The relay needs the listener-feature update before this list is available."
                    else "Could not refresh listeners. Any previous list below may be out of date."
                }
                delay(5_000)
            }
        }
    }
    ListenerDialog("listener-directory-dialog", onDismiss) {
        Text("Active listeners", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Recently active listeners across this relay. Names are self-entered and may repeat. Status is approximate.", fontSize = 13.sp, color = Color(0xff555555))
        error?.let { Text(it, color = Color(0xffb3261e), fontSize = 13.sp) }
        if (asOf == null && error == null) Text("Loading…", color = Color.Black)
        asOf?.let { time ->
            Text("${listeners.size} listeners · updated ${ActivityTime.text(time, ZoneId.systemDefault())}", fontSize = 12.sp, color = Color(0xff555555))
            if (listeners.isEmpty()) Text("No active listeners", color = Color.Black)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(listeners.size) { index ->
                val listener = listeners[index]
                Text((listener.name ?: "Anonymous listener") + if (listener.self) " (you)" else "", color = Color.Black, fontSize = 15.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeButton("REFRESH", { refresh++ }, Modifier.weight(1f))
            TimeButton("CLOSE", onDismiss, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ListenerSnapshotDialog(record: ActivityRecord, onDismiss: () -> Unit) {
    val recipients = record.recipients.orEmpty()
    val collecting = record.receiptKey != null && (record.receiptExpiresAt ?: 0) > System.currentTimeMillis()
    ListenerDialog("listener-snapshot-dialog", onDismiss) {
        Text("Listeners at send", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("${recipients.size} listening · ${recipients.count { it.confirmedAt != null }} confirmed", color = Color.Black, fontSize = 14.sp)
        Text("Confirmed means saved to Inbox, not opened or read. Unconfirmed means this phone could not confirm the save; the update may still be in that listener’s Inbox.", fontSize = 12.sp, color = Color(0xff555555))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recipients, key = { it.id }) { recipient ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(recipient.name ?: "Anonymous listener", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                    Text(recipient.confirmedAt?.let { "Confirmed · ${ActivityTime.text(it, ZoneId.of(record.timeZone), includeDate = true)}" }
                        ?: if (collecting) "Awaiting confirmation" else "Unconfirmed", fontSize = 12.sp, color = Color(0xff555555))
                }
            }
        }
        TimeButton("CLOSE", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ListenerDialog(tag: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag(tag), color = Color.White,
            shape = RectangleShape, border = BorderStroke(1.dp, Color.Black)) {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}
