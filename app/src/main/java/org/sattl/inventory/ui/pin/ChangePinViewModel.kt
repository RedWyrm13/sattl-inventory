package org.sattl.inventory.ui.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.data.repo.ChangePinResult
import org.sattl.inventory.security.PinRules
import org.sattl.inventory.ui.components.plusPinDigit

enum class ChangePinStep { CURRENT, NEW, CONFIRM }

data class ChangePinUiState(
    /** Null until the user row has loaded. */
    val forced: Boolean? = null,
    val step: ChangePinStep = ChangePinStep.NEW,
    val pinLength: Int = 0,
    val error: String? = null,
    val busy: Boolean = false,
)

/**
 * Change PIN (spec section 5.3).
 *  - Forced (mustChangePin, right after login): new PIN twice. Cannot equal the default PIN.
 *  - Voluntary (from the top bar): current PIN first, then the new PIN twice.
 */
class ChangePinViewModel(
    private val auth: AuthRepository,
    private val userId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePinUiState())
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

    /** Emits once when the PIN has been changed. */
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private var pin = ""
    private var currentPin: String? = null
    private var newPin = ""

    init {
        viewModelScope.launch {
            val forced = auth.getUser(userId)?.mustChangePin == true
            _state.update { it.copy(forced = forced, step = firstStep(forced)) }
        }
    }

    private fun firstStep(forced: Boolean) = if (forced) ChangePinStep.NEW else ChangePinStep.CURRENT

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
        if (s.busy || s.forced == null) return
        when (s.step) {
            ChangePinStep.CURRENT -> {
                currentPin = pin
                moveTo(ChangePinStep.NEW)
            }
            ChangePinStep.NEW -> {
                PinRules.formatError(pin)?.let { msg -> _state.update { it.copy(error = msg) }; return }
                newPin = pin
                moveTo(ChangePinStep.CONFIRM)
            }
            ChangePinStep.CONFIRM -> {
                if (pin != newPin) {
                    newPin = ""
                    moveTo(ChangePinStep.NEW, "The two new PINs did not match. Enter your new PIN again.")
                    return
                }
                save(s.forced)
            }
        }
    }

    private fun save(forced: Boolean) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = auth.changePin(userId, currentPin, newPin)
            _state.update { it.copy(busy = false) }
            when (result) {
                ChangePinResult.Success -> {
                    clearSecrets()
                    _done.send(Unit)
                }
                is ChangePinResult.Invalid -> {
                    clearSecrets()
                    moveTo(firstStep(forced), result.message)
                }
            }
        }
    }

    private fun moveTo(step: ChangePinStep, error: String? = null) {
        pin = ""
        _state.update { it.copy(step = step, pinLength = 0, error = error) }
    }

    private fun clearSecrets() {
        pin = ""
        currentPin = null
        newPin = ""
    }
}
