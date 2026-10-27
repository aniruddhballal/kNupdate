package com.knupdate.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.knupdate.app.ui.theme.KNupdateTheme
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Renders synthetic receipt data only; never saves history, polls, or publishes. */
class ListenerReceiptUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun duplicateNamesHaveSeparateStatusesAndUnconfirmedDoesNotMeanMissed() {
        val now = System.currentTimeMillis()
        val first = ListenerReceipt(UUID.randomUUID().toString(), "Alex", now)
        val second = ListenerReceipt(UUID.randomUUID().toString(), "Alex")
        val anonymous = ListenerReceipt(UUID.randomUUID().toString(), null)
        var record by mutableStateOf(ActivityRecord(title = "Run", body = "5k", startAt = now - 60_000,
            endAt = now - 1, listeners = 3, status = "sent", recipients = listOf(first, second, anonymous), receiptExpiresAt = now + 30_000))
        var open by mutableStateOf(true)
        compose.setContent { KNupdateTheme { if (open) ListenerSnapshotDialog(record) { open = false } } }
        compose.onNodeWithText("3 listening · 1 confirmed").assertExists()
        compose.onAllNodesWithText("Alex").assertCountEquals(2)
        compose.onNodeWithText("Anonymous listener").assertExists()
        compose.onAllNodesWithText("Awaiting confirmation").assertCountEquals(2)
        compose.runOnIdle { record = record.copy(recipients = listOf(first, second.copy(confirmedAt = now + 1), anonymous)) }
        compose.onNodeWithText("3 listening · 2 confirmed").assertExists()
        compose.runOnIdle { record = record.copy(receiptKey = null) }
        compose.onNodeWithText("Unconfirmed").assertExists()
        compose.onNodeWithText("the update may still be in that listener’s Inbox.", substring = true).assertExists()
        compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithTag("listener-snapshot-dialog").assertDoesNotExist()
    }
}
