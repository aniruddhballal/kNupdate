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
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listeners by remember { mutableStateOf<List<ActiveListener>>(emptyList()) }
    var peers by remember { mutableStateOf<List<PairedDevice>>(emptyList()) }
    var asOf by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pairingMessage by remember { mutableStateOf<String?>(null) }
    var inviteCode by rememberSaveable { mutableStateOf<String?>(null) }
    var enteredCode by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    val client = RelayClient.app(context)
                    client.register(DeviceIdentity.displayName(context))
                    peers = client.peers()
                    val result = client.listeners(ReceiverStatus.session)
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
        Text("Private connections", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Pair devices first. Names are labels; delivery uses paired device identities.", fontSize = 13.sp, color = Color(0xff555555))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlackButton("CREATE INVITE", onClick = {
                scope.launch {
                    try {
                        val client = RelayClient.app(context)
                        client.register(DeviceIdentity.displayName(context))
                        inviteCode = client.invite().getString("code")
                        pairingMessage = "Share this code with the other device. It expires in 10 minutes."
                    } catch (_: Exception) { pairingMessage = "Could not create an invite." }
                }
            }, modifier = Modifier.weight(1f))
            TimeButton("REFRESH", { refresh++ }, Modifier.weight(1f))
        }
        inviteCode?.let { code ->
            Text(code, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        LocalTextField(enteredCode, { enteredCode = it.uppercase() }, "Enter invite code", onDone = {})
        BlackButton("PAIR DEVICE", enabled = enteredCode.trim().length == 10, onClick = {
            scope.launch {
                try {
                    val client = RelayClient.app(context)
                    client.register(DeviceIdentity.displayName(context))
                    client.acceptInvite(enteredCode.trim())
                    enteredCode = ""
                    pairingMessage = "Devices paired."
                    peers = client.peers()
                } catch (_: Exception) { pairingMessage = "Could not pair. Check the code and try again." }
            }
        }, modifier = Modifier.fillMaxWidth())
        pairingMessage?.let { Text(it, color = Color(0xff555555), fontSize = 12.sp) }
        Text("Paired devices", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        if (peers.isEmpty()) Text("No paired devices", color = Color(0xff555555), fontSize = 13.sp)
        peers.forEach { peer ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(peer.label(), color = Color.Black, fontSize = 15.sp)
                TimeButton("REMOVE", {
                    scope.launch {
                        runCatching {
                            val client = RelayClient.app(context)
                            client.register(DeviceIdentity.displayName(context))
                            client.unpair(peer.id)
                            peers = client.peers()
                        }
                    }
                })
            }
        }
        Text("Active listeners", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Recently active listeners. Status is approximate.", fontSize = 13.sp, color = Color(0xff555555))
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
            TimeButton("CLOSE", onDismiss, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun RecipientSelectionDialog(onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val context = LocalContext.current
    var peers by remember { mutableStateOf<List<PairedDevice>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val client = RelayClient.app(context)
            client.register(DeviceIdentity.displayName(context))
            peers = client.peers()
        } catch (_: Exception) { error = "Could not load paired devices." }
    }
    ListenerDialog("recipient-selection-dialog", onDismiss) {
        Text("Send to", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Select exactly who may receive this kNupdate.", fontSize = 13.sp, color = Color(0xff555555))
        error?.let { Text(it, color = Color(0xffb3261e), fontSize = 13.sp) }
        if (peers.isEmpty() && error == null) Text("No paired devices yet. Pair a device first.", color = Color(0xff555555), fontSize = 14.sp)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(peers, key = { it.id }) { peer ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(selected.contains(peer.id), onCheckedChange = { checked ->
                        selected = if (checked) selected + peer.id else selected - peer.id
                    })
                    Text(peer.label(), color = Color.Black, fontSize = 15.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeButton("CANCEL", onDismiss, Modifier.weight(1f))
            BlackButton("SEND", enabled = selected.isNotEmpty(), onClick = { onConfirm(selected.toList()) }, modifier = Modifier.weight(1f))
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
