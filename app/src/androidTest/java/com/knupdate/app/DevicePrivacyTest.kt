package com.knupdate.app

import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only checks on the installed app. Reports counts, never names or activity contents. */
@RunWith(AndroidJUnit4::class)
class DevicePrivacyTest {
    @Test fun inspectLocalRetentionWithoutExportingDatabase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val backupEnabled = context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        assertFalse("The installed app must exclude Android cloud backup", backupEnabled)
        val database = context.getDatabasePath("activities.db")
        assertTrue("The app's history database must exist on the phone", database.exists())
        val report = JSONObject().put("backupEnabled", backupEnabled)
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            fun count(where: String): Long = db.rawQuery("SELECT COUNT(*) FROM activities WHERE $where", null).use {
                check(it.moveToFirst())
                it.getLong(0)
            }
            report.put("sentCount", count("direction = 'sent'"))
            report.put("receivedCount", count("direction = 'received'"))
            report.put("acceptedWithZeroListeners", count("direction = 'sent' AND status = 'sent' AND listeners = 0"))
            report.put("unconfirmedSends", count("direction = 'sent' AND status = 'uncertain'"))
            report.put("originalHomeSendAcceptedWithZeroListeners", count("direction = 'sent' AND status = 'sent' AND listeners = 0 AND title = 'Current location' AND body = 'Home'") > 0)
            report.put("syntheticDeviceChecksReceived", count("direction = 'received' AND title = 'Verification test' AND body LIKE 'Synthetic ADB check %'"))
        }
        val notifications = context.getSystemService(NotificationManager::class.java).activeNotifications
        report.put("ownActiveNotificationCount", notifications.size)
        report.put("syntheticNotificationPresent", notifications.any {
            it.notification.extras.getString("android.title") == "Verification test"
        })
        instrumentation.sendStatus(0, Bundle().apply { putString("knupdatePrivacyAudit", report.toString()) })
    }
}
