package com.fabula.audio

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ProcessingCancellationTest {
    @Test fun cancelsCurrentSessionOnlyOnce() {
        val token = ProcessingCancellation()
        var calls = 0
        token.attach { calls++ }
        token.cancel()
        token.cancel()
        assertEquals(1, calls)
        assertTrue(token.isCancelled)
    }

    @Test fun cancellationBeforeSessionRegistrationStillStopsIt() {
        val token = ProcessingCancellation()
        token.cancel()
        var stopped = false
        token.attach { stopped = true }
        assertTrue(stopped)
    }

    @Test fun completedSessionAndNextJobAreUnaffected() {
        val token = ProcessingCancellation()
        token.attach { fail("Completed session must not be cancelled") }
        token.detach()
        token.cancel()
        val next = ProcessingCancellation()
        next.check()
        assertFalse(next.isCancelled)
    }

    @Test(expected = CancellationException::class)
    fun cancelledJobCannotStartAnotherStage() {
        val token = ProcessingCancellation()
        token.cancel()
        token.check()
    }
}
