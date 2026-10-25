package com.knupdate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.knupdate.app.auth.ApiClient
import com.knupdate.app.auth.ActivityRepository
import com.knupdate.app.auth.ActivityResponse
import com.knupdate.app.auth.ConnectionRepository
import com.knupdate.app.auth.ConnectionSummary
import com.knupdate.app.auth.AuthRepository
import com.knupdate.app.auth.AuthSession
import com.knupdate.app.auth.CreateActivityRequest
import com.knupdate.app.auth.IncomingConnectionRequest
import com.knupdate.app.ui.theme.KNupdateTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KNupdateTheme {
                KNupdateApp()
            }
        }
    }
}

@Composable
private fun KNupdateApp() {
    val authRepository = remember {
        AuthRepository(ApiClient.httpClient, BuildConfig.API_BASE_URL)
    }
    val scope = rememberCoroutineScope()
    var authState by remember { mutableStateOf<AuthUiState>(AuthUiState.SignedOut) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    when (val state = authState) {
        AuthUiState.SignedOut -> SignInScreen(
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
            onContinue = { phoneNumber ->
                isSubmitting = true
                errorMessage = null
                scope.launch {
                    authRepository.requestOtp(phoneNumber).fold(
                        onSuccess = { challenge ->
                            authState = AuthUiState.AwaitingOtp(
                                phoneNumber = phoneNumber,
                                challengeId = challenge.challengeId
                            )
                            isSubmitting = false
                        },
                        onFailure = {
                            errorMessage = it.message ?: "Unable to request a verification code."
                            isSubmitting = false
                        }
                    )
                }
            }
        )

        is AuthUiState.AwaitingOtp -> VerifyOtpScreen(
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
            onBack = {
                authState = AuthUiState.SignedOut
                errorMessage = null
            },
            onVerify = { otp, displayName ->
                isSubmitting = true
                errorMessage = null
                scope.launch {
                    authRepository.verifyOtp(state.challengeId, otp, displayName).fold(
                        onSuccess = { session ->
                            authState = AuthUiState.SignedIn(session)
                            isSubmitting = false
                        },
                        onFailure = {
                            errorMessage = it.message ?: "Unable to verify the code."
                            isSubmitting = false
                        }
                    )
                }
            }
        )

        is AuthUiState.SignedIn -> AuthenticatedShell(
            session = state.session,
            onSignOut = {
                authState = AuthUiState.SignedOut
                errorMessage = null
            }
        )
    }
}

private sealed interface AuthUiState {
    data object SignedOut : AuthUiState
    data class AwaitingOtp(val phoneNumber: String, val challengeId: String) : AuthUiState
    data class SignedIn(val session: AuthSession) : AuthUiState
}

@Composable
private fun SignInScreen(
    isSubmitting: Boolean,
    errorMessage: String?,
    onContinue: (String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }

    AuthScreenScaffold {
        Text(
            text = "A private, temporary activity ledger for people already close to you.",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "No public account, followers, likes, messages, or archive.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone number") },
            singleLine = true,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )
        errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { onContinue(phoneNumber.trim()) },
            enabled = phoneNumber.isNotBlank() && !isSubmitting
        ) {
            Text(if (isSubmitting) "Requesting…" else "Continue")
        }
    }
}

@Composable
private fun VerifyOtpScreen(
    isSubmitting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onVerify: (otp: String, displayName: String) -> Unit
) {
    var otp by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AuthScreenScaffold {
        Text(
            text = "Verify your number",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Enter the code and choose the name your connections will see.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it },
            label = { Text("Verification code") },
            singleLine = true,
            enabled = !isSubmitting
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            singleLine = true,
            enabled = !isSubmitting
        )
        errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { onVerify(otp.trim(), displayName.trim()) },
            enabled = otp.isNotBlank() && displayName.isNotBlank() && !isSubmitting
        ) {
            Text(if (isSubmitting) "Verifying…" else "Verify")
        }
        TextButton(onClick = onBack, enabled = !isSubmitting) {
            Text("Use a different number")
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AuthScreenScaffold(content: @Composable () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("kNupdate") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AuthenticatedShell(
    session: AuthSession,
    onSignOut: () -> Unit
) {
    var destination by remember { mutableStateOf(AppDestination.FEED) }
    val activityRepository = remember {
        ActivityRepository(ApiClient.httpClient, BuildConfig.API_BASE_URL)
    }
    val connectionRepository = remember {
        ConnectionRepository(ApiClient.httpClient, BuildConfig.API_BASE_URL)
    }
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<List<ActivityResponse>>(emptyList()) }
    var isFeedLoading by remember { mutableStateOf(false) }
    var activityError by remember { mutableStateOf<String?>(null) }
    var isActivitySubmitting by remember { mutableStateOf(false) }
    var connections by remember { mutableStateOf<List<ConnectionSummary>>(emptyList()) }
    var incomingRequests by remember { mutableStateOf<List<IncomingConnectionRequest>>(emptyList()) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var connectionCodeInput by remember { mutableStateOf("") }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var isConnectionsLoading by remember { mutableStateOf(false) }

    suspend fun loadConnections() {
        isConnectionsLoading = true
        connectionError = null
        connectionRepository.listConnections(session).fold(
            onSuccess = { connections = it },
            onFailure = { connectionError = it.message ?: "Unable to load connections." }
        )
        connectionRepository.incomingRequests(session).fold(
            onSuccess = { incomingRequests = it },
            onFailure = { connectionError = it.message ?: "Unable to load connection requests." }
        )
        isConnectionsLoading = false
    }

    LaunchedEffect(session.accessToken) {
        isFeedLoading = true
        activityRepository.feed(session).fold(
            onSuccess = {
                feed = it
                activityError = null
            },
            onFailure = { activityError = it.message ?: "Unable to load the feed." }
        )
        isFeedLoading = false
        loadConnections()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("kNupdate") }
            )
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Text(item.title.first().toString()) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (destination) {
                AppDestination.FEED -> FeedScreen(
                    activities = feed,
                    isLoading = isFeedLoading,
                    errorMessage = activityError
                )
                AppDestination.ADD -> AddActivityScreen(
                    displayName = session.user.displayName,
                    isSubmitting = isActivitySubmitting,
                    errorMessage = activityError,
                    onCreateActivity = { request ->
                        scope.launch {
                            isActivitySubmitting = true
                            activityError = null
                            activityRepository.create(session, request).fold(
                                onSuccess = {
                                    isActivitySubmitting = false
                                    destination = AppDestination.FEED
                                    isFeedLoading = true
                                    activityRepository.feed(session).fold(
                                        onSuccess = {
                                            feed = it
                                            activityError = null
                                        },
                                        onFailure = {
                                            activityError = it.message ?: "Unable to load the feed."
                                        }
                                    )
                                    isFeedLoading = false
                                },
                                onFailure = {
                                    isActivitySubmitting = false
                                    activityError = it.message ?: "Unable to create the activity."
                                }
                            )
                        }
                    }
                )
                AppDestination.PROFILE -> ProfileScreen(
                    displayName = session.user.displayName,
                    connections = connections,
                    incomingRequests = incomingRequests,
                    generatedCode = generatedCode,
                    connectionCodeInput = connectionCodeInput,
                    connectionError = connectionError,
                    isLoading = isConnectionsLoading,
                    onConnectionCodeInputChange = { connectionCodeInput = it },
                    onCreateCode = {
                        scope.launch {
                            connectionRepository.createCode(session).fold(
                                onSuccess = { generatedCode = it.code },
                                onFailure = {
                                    connectionError = it.message ?: "Unable to create a connection code."
                                }
                            )
                        }
                    },
                    onRequestConnection = {
                        scope.launch {
                            connectionRepository.requestConnection(session, connectionCodeInput.trim()).fold(
                                onSuccess = {
                                    connectionCodeInput = ""
                                    connectionError = "Connection request sent."
                                },
                                onFailure = {
                                    connectionError = it.message ?: "Unable to send the connection request."
                                }
                            )
                        }
                    },
                    onAcceptRequest = { requestId ->
                        scope.launch {
                            connectionRepository.acceptRequest(session, requestId).fold(
                                onSuccess = { loadConnections() },
                                onFailure = {
                                    connectionError = it.message ?: "Unable to accept the request."
                                }
                            )
                        }
                    },
                    onDeclineRequest = { requestId ->
                        scope.launch {
                            connectionRepository.declineRequest(session, requestId).fold(
                                onSuccess = { loadConnections() },
                                onFailure = {
                                    connectionError = it.message ?: "Unable to decline the request."
                                }
                            )
                        }
                    },
                    onRemoveConnection = { connectionId ->
                        scope.launch {
                            connectionRepository.removeConnection(session, connectionId).fold(
                                onSuccess = { loadConnections() },
                                onFailure = {
                                    connectionError = it.message ?: "Unable to remove the connection."
                                }
                            )
                        }
                    },
                    onSignOut = onSignOut
                )
            }
        }
    }
}

private enum class AppDestination(val title: String) {
    FEED("Feed"),
    ADD("Add"),
    PROFILE("Profile")
}

@Composable
private fun FeedScreen(
    activities: List<ActivityResponse>,
    isLoading: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            isLoading -> Text("Loading activity updates…")
            activities.isEmpty() -> Text(
                text = "No current activity updates.",
                style = MaterialTheme.typography.titleMedium
            )
            else -> activities.forEachIndexed { index, activity ->
                Text(
                    text = activity.renderedText,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (index < activities.lastIndex) HorizontalDivider()
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AddActivityScreen(
    displayName: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCreateActivity: (CreateActivityRequest) -> Unit
) {
    var selectedActivity by rememberSaveable { mutableStateOf(ACTIVITY_OPTIONS.first().first) }
    var activityMenuExpanded by remember { mutableStateOf(false) }
    var startTime by rememberSaveable { mutableStateOf("09:30") }
    var endTime by rememberSaveable { mutableStateOf("10:00") }
    var selectedQuantityType by rememberSaveable { mutableStateOf<String?>(null) }
    var quantityMenuExpanded by remember { mutableStateOf(false) }
    var quantityValue by rememberSaveable { mutableStateOf("") }

    val validationError = validateActivityInput(
        startTime = startTime,
        endTime = endTime,
        quantityType = selectedQuantityType,
        quantityValue = quantityValue
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add activity", style = MaterialTheme.typography.titleLarge)
        Text("Choose from the fixed activity list. There is no caption or proof field.")

        Box {
            OutlinedButton(onClick = { activityMenuExpanded = true }) {
                Text(activityLabel(selectedActivity))
            }
            DropdownMenu(
                expanded = activityMenuExpanded,
                onDismissRequest = { activityMenuExpanded = false }
            ) {
                ACTIVITY_OPTIONS.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedActivity = value
                            activityMenuExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = startTime,
            onValueChange = { startTime = it },
            label = { Text("Start time (HH:mm)") },
            singleLine = true,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = endTime,
            onValueChange = { endTime = it },
            label = { Text("End time (HH:mm)") },
            singleLine = true,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        Box {
            OutlinedButton(onClick = { quantityMenuExpanded = true }) {
                Text(quantityLabel(selectedQuantityType))
            }
            DropdownMenu(
                expanded = quantityMenuExpanded,
                onDismissRequest = { quantityMenuExpanded = false }
            ) {
                QUANTITY_OPTIONS.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedQuantityType = value
                            quantityMenuExpanded = false
                            if (value == null) quantityValue = ""
                        }
                    )
                }
            }
        }

        if (selectedQuantityType != null) {
            OutlinedTextField(
                value = quantityValue,
                onValueChange = { quantityValue = it },
                label = { Text("Quantity") },
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Preview", style = MaterialTheme.typography.titleMedium)
        Text(
            text = activityPreview(
                displayName,
                selectedActivity,
                startTime,
                endTime,
                selectedQuantityType,
                quantityValue
            )
        )

        (errorMessage ?: validationError)?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                onCreateActivity(
                    CreateActivityRequest(
                        activityType = selectedActivity,
                        startedAt = activityDateTime(startTime),
                        endedAt = activityDateTime(endTime),
                        quantityType = selectedQuantityType,
                        quantityValue = quantityValue.toDoubleOrNull()
                    )
                )
            },
            enabled = validationError == null && !isSubmitting
        ) {
            Text(if (isSubmitting) "Sending…" else "Notify connections")
        }
    }
}

private val ACTIVITY_OPTIONS = listOf(
    "run" to "Run",
    "walk" to "Walk",
    "cycle" to "Cycle",
    "swim" to "Swim",
    "strength_training" to "Strength training",
    "yoga" to "Yoga",
    "sport_game" to "Sport / game",
    "study" to "Study",
    "read" to "Read",
    "work" to "Work",
    "cook" to "Cook",
    "travel" to "Travel",
    "rest" to "Rest",
    "other" to "Other structured activity"
)

private val QUANTITY_OPTIONS = listOf(
    null to "No quantity",
    "distance" to "Distance (km)",
    "pages" to "Pages",
    "minutes" to "Minutes",
    "sessions" to "Sessions"
)

private fun activityLabel(value: String): String =
    ACTIVITY_OPTIONS.first { it.first == value }.second

private fun quantityLabel(value: String?): String =
    QUANTITY_OPTIONS.first { it.first == value }.second

private fun validateActivityInput(
    startTime: String,
    endTime: String,
    quantityType: String?,
    quantityValue: String
): String? {
    val start = parseTime(startTime) ?: return "Enter a start time as HH:mm."
    val end = parseTime(endTime) ?: return "Enter an end time as HH:mm."
    if (!end.isAfter(start)) return "End time must be after start time."
    if (quantityType != null && (quantityValue.toDoubleOrNull()?.let { it > 0.0 } != true)) {
        return "Enter a positive quantity or choose no quantity."
    }
    return null
}

private fun parseTime(value: String): LocalTime? =
    try {
        LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm"))
    } catch (_: DateTimeParseException) {
        null
    }

private fun activityDateTime(value: String): String {
    val zone = ZoneId.systemDefault()
    val offset = zone.rules.getOffset(java.time.Instant.now())
    return OffsetDateTime.of(LocalDate.now(), parseTime(value)!!, offset).toString()
}

private fun activityPreview(
    displayName: String,
    activityType: String,
    startTime: String,
    endTime: String,
    quantityType: String?,
    quantityValue: String
): String {
    val start = parseTime(startTime) ?: return "Enter valid times to preview the activity."
    val end = parseTime(endTime) ?: return "Enter valid times to preview the activity."
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    val quantity = if (quantityType == null) "" else " · $quantityValue ${quantityUnit(quantityType)}"
    return "$displayName completed an activity — ${activityLabel(activityType)}$quantity · ${start.format(formatter)}–${end.format(formatter)}"
}

private fun quantityUnit(value: String): String = when (value) {
    "distance" -> "km"
    "pages" -> "pages"
    "minutes" -> "minutes"
    "sessions" -> "sessions"
    else -> ""
}

@Composable
private fun ProfileScreen(
    displayName: String,
    connections: List<ConnectionSummary>,
    incomingRequests: List<IncomingConnectionRequest>,
    generatedCode: String?,
    connectionCodeInput: String,
    connectionError: String?,
    isLoading: Boolean,
    onConnectionCodeInputChange: (String) -> Unit,
    onCreateCode: () -> Unit,
    onRequestConnection: () -> Unit,
    onAcceptRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onRemoveConnection: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge
        )

        Text("Connections", style = MaterialTheme.typography.titleMedium)
        if (isLoading) {
            Text("Loading connections…", style = MaterialTheme.typography.bodyMedium)
        } else if (connections.isEmpty()) {
            Text("No current connections.", style = MaterialTheme.typography.bodyMedium)
        } else {
            connections.forEach { connection ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(connection.displayName)
                    TextButton(onClick = { onRemoveConnection(connection.id) }) {
                        Text("Remove")
                    }
                }
            }
        }

        TextButton(onClick = onCreateCode) {
            Text("Create connection code")
        }
        generatedCode?.let { code ->
            Text("Give this code directly to the person you want to connect with:")
            Text(code, style = MaterialTheme.typography.titleMedium)
        }

        OutlinedTextField(
            value = connectionCodeInput,
            onValueChange = onConnectionCodeInputChange,
            label = { Text("Connection code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onRequestConnection,
            enabled = connectionCodeInput.isNotBlank()
        ) {
            Text("Request connection")
        }

        if (incomingRequests.isNotEmpty()) {
            Text("Incoming requests", style = MaterialTheme.typography.titleMedium)
            incomingRequests.forEach { request ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(request.requesterDisplayName)
                    Text("Shared connections: ${request.sharedMutualCount}")
                    TextButton(onClick = { onAcceptRequest(request.id) }) {
                        Text("Accept")
                    }
                    TextButton(onClick = { onDeclineRequest(request.id) }) {
                        Text("Decline")
                    }
                }
            }
        }

        connectionError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = onSignOut) {
            Text("Sign out")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KNupdatePreview() {
    KNupdateTheme {
        KNupdateApp()
    }
}
