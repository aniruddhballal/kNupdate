package com.knupdate.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationSendServiceTest {
    @Test fun scheduleWaitsWithoutReadingLocationAndCancellationDoesNotPublish() {
        val context: Application = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = context.getSystemService(LocationManager::class.java)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val store = ActivityStore.get(context)
        val record = ActivityRecord(title = "Current location", body = "Outside",
            startAt = System.currentTimeMillis() + 3_600_000, endAt = null)
        val intent = Intent(context, LocationSendService::class.java).putExtra("id", record.id)
            .putExtra("title", record.title).putExtra("body", record.body)
            .putExtra("at", record.startAt).putExtra("zone", record.timeZone)
        val service = Robolectric.buildService(LocationSendService::class.java, intent).create().startCommand(0, 1)
        fun awaitState(predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000
            while (!predicate() && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue(predicate())
        }
        try {
            awaitState { store.records.value.any { it.id == record.id && it.status == "scheduled" } &&
                shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isNotEmpty() }
            assertTrue(LocationSendService.active.value)
            assertTrue(shadowOf(manager).getLocationUpdateListeners().isEmpty())
            assertNull(store.records.value.single { it.id == record.id }.coordinates)
        } finally { service.destroy() }
        awaitState { store.records.value.any { it.id == record.id && it.status == "cancelled" } &&
            shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty() }
        assertFalse(LocationSendService.active.value)
        assertTrue(shadowOf(manager).getLocationUpdateListeners().isEmpty())
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())

        // Alarm access can be revoked after the visible activity checked it.
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val deniedId = java.util.UUID.randomUUID().toString()
        val denied = Robolectric.buildService(LocationSendService::class.java,
            Intent(intent).putExtra("id", deniedId)).create().startCommand(0, 2)
        try {
            awaitState { store.records.value.any { it.id == deniedId && it.status == "location_failed" } }
            assertTrue(shadowOf(manager).getLocationUpdateListeners().isEmpty())
            assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        } finally { denied.destroy() }
    }
}
