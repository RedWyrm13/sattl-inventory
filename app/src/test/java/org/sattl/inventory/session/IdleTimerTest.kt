package org.sattl.inventory.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** Rule 6.14: warning at 1:45 idle, logout at 2:00. */
class IdleTimerTest {
    private val timer = IdleTimer()

    @Test
    fun activeBeforeWarningWindow() {
        assertEquals(IdleState.Active, timer.stateFor(0))
        assertEquals(IdleState.Active, timer.stateFor(104_999))
    }

    @Test
    fun warningCountsDownFromFifteenSeconds() {
        assertEquals(IdleState.Warning(15), timer.stateFor(105_000))
        assertEquals(IdleState.Warning(10), timer.stateFor(110_000))
        assertEquals(IdleState.Warning(1), timer.stateFor(119_999))
    }

    @Test
    fun expiresAtTwoMinutes() {
        assertEquals(IdleState.Expired, timer.stateFor(120_000))
        assertEquals(IdleState.Expired, timer.stateFor(500_000))
    }
}
