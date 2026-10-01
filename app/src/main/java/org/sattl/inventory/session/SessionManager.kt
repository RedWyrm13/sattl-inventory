package org.sattl.inventory.session

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User

/** The logged-in person. Only the fields the UI needs; never the PIN hash. */
data class SessionUser(val id: Long, val name: String, val role: Role) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}

/**
 * Holds who is logged in and runs the inactivity auto-logout (rule 6.14).
 *
 * MainActivity calls [onUserInteraction] on every touch. While someone is logged in, a
 * once-a-second ticker asks [IdleTimer] whether to show the warning or log out.
 *
 * [elapsedRealtime] is a monotonic clock (it does not jump if the admin changes the date),
 * and is replaceable in tests.
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val idleTimer: IdleTimer = IdleTimer(),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _currentUser = MutableStateFlow<SessionUser?>(null)
    val currentUser: StateFlow<SessionUser?> = _currentUser.asStateFlow()

    /** Seconds left before auto-logout while the "Still there?" warning should show, else null. */
    private val _idleWarningSeconds = MutableStateFlow<Int?>(null)
    val idleWarningSeconds: StateFlow<Int?> = _idleWarningSeconds.asStateFlow()

    @Volatile
    private var lastInteraction = elapsedRealtime()
    private var ticker: Job? = null

    fun login(user: User) {
        _currentUser.value = SessionUser(user.id, user.name, user.role)
        onUserInteraction()
        startTicker()
    }

    /**
     * Ends the session. Called by the Log out button, by the idle timer, and (from milestone 3)
     * right after a successful checkout or check-in (rule 6.14).
     */
    fun logout() {
        ticker?.cancel()
        ticker = null
        _idleWarningSeconds.value = null
        _currentUser.value = null
    }

    /** Any touch resets the inactivity timer and dismisses the warning. */
    fun onUserInteraction() {
        lastInteraction = elapsedRealtime()
        _idleWarningSeconds.value = null
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                when (val state = idleTimer.stateFor(elapsedRealtime() - lastInteraction)) {
                    IdleState.Active -> _idleWarningSeconds.value = null
                    is IdleState.Warning -> _idleWarningSeconds.value = state.secondsLeft
                    IdleState.Expired -> {
                        logout()
                        return@launch
                    }
                }
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1000L
    }
}
