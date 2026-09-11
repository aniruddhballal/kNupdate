package com.knupdate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.knupdate.app.auth.ApiClient
import com.knupdate.app.auth.AuthRepository
import com.knupdate.app.auth.AuthSession
import com.knupdate.app.ui.theme.KNupdateTheme
import kotlinx.coroutines.launch

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
private fun AuthenticatedShell(
    session: AuthSession,
    onSignOut: () -> Unit
) {
    var destination by remember { mutableStateOf(AppDestination.FEED) }

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
                AppDestination.FEED -> FeedPlaceholder()
                AppDestination.ADD -> AddActivityPlaceholder()
                AppDestination.PROFILE -> ProfilePlaceholder(
                    displayName = session.user.displayName,
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
private fun FeedPlaceholder() {
    Text(
        text = "No current activity updates.",
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun AddActivityPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Add activity",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Structured activity entry will be connected next.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProfilePlaceholder(displayName: String, onSignOut: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Profile and settings will be connected next.",
            style = MaterialTheme.typography.bodyMedium
        )
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
