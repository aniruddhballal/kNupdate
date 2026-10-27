package com.knupdate.app

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationCaptureTest {
    private lateinit var context: Application
    private lateinit var manager: LocationManager

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(LocationManager::class.java)
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        SystemClock.sleep(5_000)
    }

    private fun fix(elapsed: Long) = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = 12.345678; longitude = -76.54321; accuracy = 24.5f
        time = System.currentTimeMillis(); elapsedRealtimeNanos = elapsed
    }

    @Test fun cachedFixIsIgnoredAndOnlyANewSnapshotCompletesTheRequest() = runBlocking {
        val old = fix(SystemClock.elapsedRealtimeNanos() - 1_000_000_000)
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, old)
        val request = async(start = CoroutineStart.UNDISPATCHED) { LocationCapture.fresh(context) }
        shadowOf(manager).simulateLocation(old)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(request.isCompleted)
        val fresh = fix(SystemClock.elapsedRealtimeNanos())
        shadowOf(manager).simulateLocation(fresh)
        shadowOf(Looper.getMainLooper()).idle()
        val result = request.await()
        assertEquals(fresh.latitude, result.latitude, 0.0)
        assertEquals(fresh.longitude, result.longitude, 0.0)
        assertEquals(fresh.time, result.capturedAt)
        assertTrue(shadowOf(manager).getLocationUpdateListeners().isEmpty())
    }

    @Test fun cancellingASnapshotUnsubscribesFromAllProviders() = runBlocking {
        val request = async(start = CoroutineStart.UNDISPATCHED) { LocationCapture.fresh(context) }
        assertFalse(shadowOf(manager).getLocationUpdateListeners().isEmpty())
        request.cancelAndJoin()
        assertTrue(shadowOf(manager).getLocationUpdateListeners().isEmpty())
    }

    @Test fun disabledLocationFailsWithoutReturningACachedFix() = runBlocking {
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        assertFalse(LocationCapture.enabled(context))
        val failure = runCatching { LocationCapture.fresh(context) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(shadowOf(manager).getLocationUpdateListeners().isEmpty())
    }
}
