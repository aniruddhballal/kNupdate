package com.knupdate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.knupdate.app.ui.theme.KNupdateTheme

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
    var destination by rememberSaveable { mutableStateOf(AppDestination.FEED) }

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
                AppDestination.PROFILE -> ProfilePlaceholder()
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
private fun ProfilePlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Sign-in and profile details will be connected next.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun KNupdatePreview() {
    KNupdateTheme {
        KNupdateApp()
    }
}
