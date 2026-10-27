package com.knupdate.app

import android.content.Context
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListenerReceiptTest {
    @Test fun namesArePrivateUntilSharingIsExplicitlyChosen() {
        val context: Context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("knupdate_local", 0)
        preferences.edit().clear().putString("display_name", "  अनिरुद्ध  ").commit()
        assertNull(ListenerNames.sharedName(context))
        preferences.edit().putBoolean("share_listener_name", true).commit()
        assertEquals("अनिरुद्ध", ListenerNames.sharedName(context))
        preferences.edit().putBoolean("share_listener_name", false).commit()
        assertNull(ListenerNames.sharedName(context))
    }

    @Test fun nameValidationAllowsUnicodeButRejectsBlankLongAndControlText() {
        assertTrue(ListenerNames.valid("Alex")); assertTrue(ListenerNames.valid("अनिरुद्ध"))
        for (value in listOf(" ", "a".repeat(81), "A\nB", "A\u202eB")) assertFalse(value, ListenerNames.valid(value))
    }

    @Test fun duplicateNamesHaveSeparateReceiptIdsAndAnonymousRemainsAnonymous() {
        val recipients = listOf(ListenerReceipt(UUID.randomUUID().toString(), "Alex"),
            ListenerReceipt(UUID.randomUUID().toString(), "Alex"), ListenerReceipt(UUID.randomUUID().toString(), null))
        assertEquals(recipients, ListenerReceipt.parse(JSONArray(ListenerReceipt.json(recipients))))
        assertThrows(IllegalArgumentException::class.java) {
            ListenerReceipt.parse(JSONArray(ListenerReceipt.json(listOf(recipients.first(), recipients.first()))))
        }
    }
}
