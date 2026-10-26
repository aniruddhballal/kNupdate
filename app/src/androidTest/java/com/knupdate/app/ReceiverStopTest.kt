package com.knupdate.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiverStopTest {
    @Test fun stopImmediatelyInvalidatesCallbacksAndStaysStopped() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val run = ReceiverStatus.begin()
        ReceiverStatus.update(run, "Relay unavailable - retrying")
        instrumentation.runOnMainSync { ReceiverService.stop(context) }
        assertFalse(ReceiverStatus.state.value.running)
        assertNull(ReceiverStatus.session)
        assertFalse(ReceiverStatus.update(run, "Relay unavailable - retrying"))
        assertEquals("Receiving is off", ReceiverStatus.state.value.message)
    }

    @Test fun notificationStopActionImmediatelyClearsRetryState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val run = ReceiverStatus.begin()
        ReceiverStatus.update(run, "Relay unavailable - retrying")
        instrumentation.runOnMainSync {
            // Exercise the actual notification action through Android's service lifecycle.
            context.startService(Intent(context, ReceiverService::class.java).setAction("com.knupdate.STOP_RECEIVING"))
        }
        val deadline = System.nanoTime() + 2_000_000_000L
        while (ReceiverStatus.state.value.running && System.nanoTime() < deadline) Thread.sleep(20)
        assertFalse(ReceiverStatus.state.value.running)
        assertFalse(ReceiverStatus.update(run, "Relay unavailable - retrying"))
    }
}
