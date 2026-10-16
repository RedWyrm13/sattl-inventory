package org.sattl.inventory.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.data.repo.RecoveryResult
import org.sattl.inventory.security.PinRules
import org.sattl.inventory.security.RecoveryCode
import org.sattl.inventory.ui.components.plusPinDigit

enum class RecoveryStep { CODE, PIN, CONFIRM_PIN, NEW_CODE, DONE }

data class RecoveryUiState(
    val step: RecoveryStep = RecoveryStep.CODE,
    /** The recovery code as typed (not a secret once used: it is replaced on success). */
    val codeText: String = "",
    val pinLength: Int = 0,
    val error: String? = null,
    /** The new code, formatted for display, shown once at NEW_CODE. */
    val newCode: String = "",
    val writtenDown: Boolean = false,
    val busy: Boolean = false,
)

/**
 * Admin PIN recovery (spec 5.10): enter the recovery code, choose a new admin PIN twice,
 * then a new recovery code is shown once, as in setup. Nothing is saved until the admin
 * ticks "I have written this down", so the old code keeps working if they stop early.
 */
class RecoveryViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RecoveryUiState())
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    private var pin = ""
    private var firstPin = ""
    private var newCode = ""

    fun onCodeChange(text: String) = _state.update { it.copy(codeText = text, error = null) }

    fun onCodeSubmit() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val ok = auth.checkRecoveryCode(_state.value.codeText)
            _state.update {
                if (ok) it.copy(busy = false, step = RecoveryStep.PIN, error = null)
                else it.copy(busy = false, error = "That recovery code is not correct. Check it and try again. Dashes and spaces do not matter.")
            }
        }
    }

    fun onDigit(d: Char) {
        pin = pin.plusPinDigit(d)
        _state.update { it.copy(pinLength = pin.length, error = null) }
    }

    fun onBackspace() {
        pin = pin.dropLast(1)
        _state.update { it.copy(pinLength = pin.length) }
    }

    fun onPinSubmit() {
        when (_state.value.step) {
            RecoveryStep.PIN -> {
                PinRules.formatError(pin)?.let { msg -> _state.update { it.copy(error = msg) }; return }
                firstPin = pin; pin = ""
                _state.update { it.copy(step = RecoveryStep.CONFIRM_PIN, pinLength = 0) }
            }
            RecoveryStep.CONFIRM_PIN -> {
                if (pin != firstPin) {
                    pin = ""; firstPin = ""
                    _state.update { it.copy(step = RecoveryStep.PIN, pinLength = 0, error = "The two PINs did not match. Choose your new PIN again.") }
                    return
                }
                newCode = RecoveryCode.generate()
                _state.update { it.copy(step = RecoveryStep.NEW_CODE, pinLength = 0, newCode = RecoveryCode.format(newCode)) }
            }
            else -> Unit
        }
    }

    fun onWrittenDownChange(checked: Boolean) = _state.update { it.copy(writtenDown = checked) }

    /** Saves the new PIN and new recovery code together. */
    fun onFinish() {
        val s = _state.value
        if (!s.writtenDown || s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.recoverAdminPin(s.codeText, firstPin, newCode)) {
                RecoveryResult.Success -> {
                    pin = ""; firstPin = ""; newCode = ""
                    _state.update { it.copy(busy = false, step = RecoveryStep.DONE, codeText = "", newCode = "") }
                }
                is RecoveryResult.Invalid -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }
}
