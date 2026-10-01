package org.sattl.inventory.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.entity.User
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.data.repo.LoginResult
import org.sattl.inventory.session.SessionManager
import org.sattl.inventory.ui.components.plusPinDigit
import org.sattl.inventory.util.Clock

data class UserOption(val id: Long, val name: String)

data class LoginUiState(
    val users: List<UserOption> = emptyList(),
    val selectedUserId: Long? = null,
    /** Only the number of digits typed is exposed, never the PIN. */
    val pinLength: Int = 0,
    val message: String? = null,
    /** Seconds until the selected user's lockout ends, or null if not locked. */
    val lockedSecondsLeft: Long? = null,
    val busy: Boolean = false,
) {
    val selectedName: String? get() = users.firstOrNull { it.id == selectedUserId }?.name
}

sealed interface LoginEvent {
    /** Logged in. [mustChangePin] sends the user to Change PIN first (spec section 5.2 step 3). */
    data class LoggedIn(val mustChangePin: Boolean) : LoginEvent
}

/**
 * Login (spec section 5.2): pick a name from the active users, type the PIN on the pad.
 * Wrong PIN shows attempts remaining; after 5 the user is locked for 5 minutes with a
 * visible countdown (spec section 8). The lockout rules themselves live in [AuthRepository].
 */
class LoginViewModel(
    private val auth: AuthRepository,
    private val session: SessionManager,
    private val clock: Clock = Clock.SYSTEM,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pin = ""
    /** Latest user rows, kept to read each user's lockedUntil for the countdown. */
    private var users: List<User> = emptyList()

    init {
        viewModelScope.launch {
            auth.activeUsers.collect { list ->
                users = list
                _state.update { s ->
                    s.copy(
                        users = list.map { UserOption(it.id, it.name) },
                        // Drop the selection if that user disappeared (e.g. deactivated).
                        selectedUserId = s.selectedUserId?.takeIf { id -> list.any { it.id == id } },
                    )
                }
                refreshLockout()
            }
        }
        // Updates the lockout countdown once a second.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                refreshLockout()
            }
        }
    }

    fun onSelectUser(id: Long) {
        pin = ""
        _state.update { it.copy(selectedUserId = id, pinLength = 0, message = null) }
        refreshLockout()
    }

    fun onDigit(d: Char) {
        if (_state.value.lockedSecondsLeft != null) return
        pin = pin.plusPinDigit(d)
        _state.update { it.copy(pinLength = pin.length, message = null) }
    }

    fun onBackspace() {
        pin = pin.dropLast(1)
        _state.update { it.copy(pinLength = pin.length) }
    }

    fun onSubmit() {
        val userId = _state.value.selectedUserId ?: return
        if (_state.value.busy || pin.isEmpty()) return
        val attempt = pin
        pin = ""
        _state.update { it.copy(busy = true, pinLength = 0) }

        viewModelScope.launch {
            val result = auth.login(userId, attempt)
            _state.update { it.copy(busy = false) }
            when (result) {
                is LoginResult.Success -> {
                    session.login(result.user)
                    _state.update { it.copy(selectedUserId = null, message = null) }
                    _events.send(LoginEvent.LoggedIn(result.user.mustChangePin))
                }
                is LoginResult.WrongPin -> _state.update {
                    val plural = if (result.attemptsRemaining == 1) "attempt" else "attempts"
                    it.copy(message = "Incorrect PIN. ${result.attemptsRemaining} $plural left before a 5-minute lockout.")
                }
                is LoginResult.LockedOut -> refreshLockout(result.lockedUntil)
                LoginResult.UserNotFound -> _state.update {
                    it.copy(selectedUserId = null, message = "That user is no longer available. Choose your name again.")
                }
            }
        }
    }

    /**
     * Recomputes the countdown for the selected user. [knownUntil] is used straight after a
     * lockout, before the updated user row arrives from the database.
     */
    private fun refreshLockout(knownUntil: Long? = null) {
        val id = _state.value.selectedUserId
        val until = knownUntil ?: users.firstOrNull { it.id == id }?.lockedUntil
        val remainingMs = (until ?: 0L) - clock.now()
        _state.update { s ->
            if (id != null && remainingMs > 0) {
                s.copy(lockedSecondsLeft = (remainingMs + 999) / 1000)
            } else if (s.lockedSecondsLeft != null) {
                // Lockout just ended (or a different user was selected): clear the message.
                s.copy(lockedSecondsLeft = null, message = null)
            } else {
                s
            }
        }
    }
}
