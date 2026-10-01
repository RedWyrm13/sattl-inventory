package org.sattl.inventory.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User

/** Rule 6.14: auto-logout after 2 minutes idle, with a 15-second warning. Uses virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val user = User(
        id = 7, name = "Sam", role = Role.USER, pinHash = "h", pinSalt = "s",
        mustChangePin = false, createdAt = 0,
    )

    private fun TestScope.newSession() =
        SessionManager(backgroundScope, elapsedRealtime = { testScheduler.currentTime })

    @Test
    fun logsOutAfterTwoMinutesIdleWithWarningFirst() = runTest {
        val session = newSession()
        session.login(user)
        assertEquals(7L, session.currentUser.value?.id)

        advanceTimeBy(100_000); runCurrent()
        assertNull("no warning before 1:45", session.idleWarningSeconds.value)

        advanceTimeBy(6_000); runCurrent()
        assertNotNull("warning shown after 1:45", session.idleWarningSeconds.value)
        assertNotNull(session.currentUser.value)

        advanceTimeBy(15_000); runCurrent()
        assertNull("logged out at 2:00", session.currentUser.value)
        assertNull(session.idleWarningSeconds.value)
    }

    @Test
    fun touchResetsTimerAndDismissesWarning() = runTest {
        val session = newSession()
        session.login(user)

        advanceTimeBy(110_000); runCurrent()
        assertNotNull(session.idleWarningSeconds.value)

        session.onUserInteraction()
        assertNull(session.idleWarningSeconds.value)

        // 1:50 after the touch the user is still logged in.
        advanceTimeBy(110_000); runCurrent()
        assertNotNull(session.currentUser.value)
    }

    @Test
    fun manualLogoutClearsSession() = runTest {
        val session = newSession()
        session.login(user)
        session.logout()
        assertNull(session.currentUser.value)
    }
}
