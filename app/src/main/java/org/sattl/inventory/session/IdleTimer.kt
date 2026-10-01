package org.sattl.inventory.session

/** What the inactivity timer says should be happening right now. */
sealed interface IdleState {
    /** User is active; nothing to show. */
    data object Active : IdleState

    /** Show the "Still there?" warning with this many whole seconds left. */
    data class Warning(val secondsLeft: Int) : IdleState

    /** Time is up: log out. */
    data object Expired : IdleState
}

/**
 * Rule 6.14: log out after 2 minutes with no touch, showing a 15-second "Still there?"
 * warning first. The warning appears at 1:45 of inactivity and logout happens at 2:00,
 * so the total idle time before logout is exactly 2 minutes.
 *
 * Pure logic with no Android or coroutine code, so it is easy to unit test.
 */
class IdleTimer(
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    private val warningMillis: Long = WARNING_MILLIS,
) {
    fun stateFor(idleMillis: Long): IdleState {
        val remaining = timeoutMillis - idleMillis
        return when {
            remaining <= 0 -> IdleState.Expired
            remaining <= warningMillis -> IdleState.Warning(((remaining + 999) / 1000).toInt())
            else -> IdleState.Active
        }
    }

    companion object {
        const val TIMEOUT_MILLIS = 2 * 60 * 1000L
        const val WARNING_MILLIS = 15 * 1000L
    }
}
