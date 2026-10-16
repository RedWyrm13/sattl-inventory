package org.sattl.inventory.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.repo.UserRepository
import org.sattl.inventory.data.repo.UserSaveResult
import org.sattl.inventory.security.PinRules
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.plusPinDigit

enum class UserPinStep { NAME, PIN, CONFIRM, DONE }

data class UserPinUiState(
    val loading: Boolean = true,
    /** True when adding a new user; false when resetting an existing user's PIN. */
    val adding: Boolean = true,
    val step: UserPinStep = UserPinStep.NAME,
    /** New user's name being typed, or the existing user's name when resetting. */
    val name: String = "",
    val pinLength: Int = 0,
    val error: String? = null,
    val busy: Boolean = false,
)

/**
 * Add user, or reset a user's PIN (spec 5.9). The admin types a default PIN twice (so a
 * typo cannot leave the person unable to log in); the person must replace it at first login.
 * [resetUserId] null means "add a new user".
 */
class UserPinViewModel(
    private val users: UserRepository,
    private val actor: SessionUser,
    private val resetUserId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(UserPinUiState(adding = resetUserId == null))
    val state: StateFlow<UserPinUiState> = _state.asStateFlow()

    // Kept out of the UI state so the PIN never reaches the UI layer.
    private var pin = ""
    private var firstPin = ""

    init {
        if (resetUserId == null) {
            _state.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch {
                val user = users.getUser(resetUserId)
                _state.update {
                    it.copy(loading = false, step = UserPinStep.PIN, name = user?.name.orEmpty(),
                        error = if (user == null) "User not found." else null)
                }
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v, error = null) }

    fun onNameNext() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val error = users.nameError(_state.value.name)
            _state.update {
                if (error != null) it.copy(busy = false, error = error)
                else it.copy(busy = false, error = null, step = UserPinStep.PIN)
            }
        }
    }

    fun onBackToName() {
        pin = ""; firstPin = ""
        _state.update { it.copy(step = UserPinStep.NAME, pinLength = 0, error = null) }
    }

    fun onDigit(d: Char) {
        pin = pin.plusPinDigit(d)
        _state.update { it.copy(pinLength = pin.length, error = null) }
    }

    fun onBackspace() {
        pin = pin.dropLast(1)
        _state.update { it.copy(pinLength = pin.length) }
    }

    fun onSubmit() {
        val s = _state.value
        if (s.busy) return
        when (s.step) {
            UserPinStep.PIN -> {
                PinRules.formatError(pin)?.let { msg -> _state.update { it.copy(error = msg) }; return }
                firstPin = pin
                pin = ""
                _state.update { it.copy(step = UserPinStep.CONFIRM, pinLength = 0) }
            }
            UserPinStep.CONFIRM -> {
                if (pin != firstPin) {
                    pin = ""; firstPin = ""
                    _state.update {
                        it.copy(step = UserPinStep.PIN, pinLength = 0, error = "The two PINs did not match. Enter the default PIN again.")
                    }
                    return
                }
                save()
            }
            else -> Unit
        }
    }

    private fun save() {
        _state.update { it.copy(busy = true) }
        val defaultPin = firstPin
        viewModelScope.launch {
            val result = if (resetUserId == null) users.addUser(actor, _state.value.name, defaultPin)
            else users.resetPin(actor, resetUserId, defaultPin)
            pin = ""; firstPin = ""
            _state.update {
                when (result) {
                    is UserSaveResult.Saved -> it.copy(busy = false, pinLength = 0, step = UserPinStep.DONE, name = result.user.name)
                    // The PIN format was already checked on the PIN step, so when adding, a
                    // failed save is a name problem (e.g. taken meanwhile): back to the name.
                    is UserSaveResult.Invalid -> it.copy(
                        busy = false, pinLength = 0, error = result.message,
                        step = if (resetUserId == null) UserPinStep.NAME else UserPinStep.PIN,
                    )
                }
            }
        }
    }
}
