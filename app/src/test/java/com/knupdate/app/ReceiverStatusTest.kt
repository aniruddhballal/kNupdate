package com.knupdate.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReceiverStatusTest {
    @Before fun prepare() { ReceiverStatus.end() }
    @After fun cleanup() { ReceiverStatus.end() }

    @Test fun lateNetworkFailureCannotUndoStop() {
        val run = ReceiverStatus.begin()
        val session = ReceiverStatus.session
        assertTrue(ReceiverStatus.update(run, "Listening for public kNupdates"))
        assertEquals(session, ReceiverStatus.end())
        assertFalse(ReceiverStatus.update(run, "Relay unavailable - retrying"))
        assertFalse(ReceiverStatus.state.value.running)
        assertEquals("Receiving is off", ReceiverStatus.state.value.message)
        assertNull(ReceiverStatus.session)
    }

    @Test fun oldReceiverCannotChangeOrStopANewReceiver() {
        val oldRun = ReceiverStatus.begin()
        ReceiverStatus.end()
        val newRun = ReceiverStatus.begin()
        val newSession = ReceiverStatus.session
        assertFalse(ReceiverStatus.update(oldRun, "Relay unavailable - retrying"))
        assertNull(ReceiverStatus.end(oldRun))
        assertNull(ReceiverStatus.rotateSession(oldRun))
        assertTrue(ReceiverStatus.isCurrent(newRun))
        assertEquals(newSession, ReceiverStatus.session)
        assertTrue(ReceiverStatus.state.value.running)
    }

    @Test fun stoppingWithoutAServiceClearsAnyDisplayedRetryStatus() {
        val run = ReceiverStatus.begin()
        ReceiverStatus.update(run, "Relay unavailable - retrying")
        ReceiverStatus.end()
        ReceiverStatus.end()
        assertEquals(ReceiverState(), ReceiverStatus.state.value)
    }

    @Test fun networkLossRotatesOnlyTheCurrentListenerSession() {
        val run = ReceiverStatus.begin()
        val previous = ReceiverStatus.session
        assertEquals(previous, ReceiverStatus.rotateSession(run))
        assertNotEquals(previous, ReceiverStatus.session)
        ReceiverStatus.end()
        assertNull(ReceiverStatus.rotateSession(run))
        assertNull(ReceiverStatus.session)
    }
}
