package org.sattl.inventory.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.security.PinRules
import org.sattl.inventory.security.RecoveryCode
import org.sattl.inventory.ui.components.plusPinDigit

/** The steps of first-time setup, in order (spec section 5.1). */
enum class SetupStep { NAME, PIN, CONFIRM_PIN, RECOVERY_CODE, DATE_TIME }

data class SetupUiState(
    val step: SetupStep = SetupStep.NAME,
    val name: String = "",
    /** Only the number of digits typed is exposed to the UI, never the PIN itself. */
    val pinLength: Int = 0,
    val error: String? = null,
    /** Formatted for display, e.g. "ABCD-EFGH-JKMN". Set at the RECOVERY_CODE step. */
    val recoveryCode: String = "",
    val writtenDown: Boolean = false,
    val saving: Boolean = false,
)

/**
 * First-time setup (spec section 5.1):
 *  1. Admin name, then a new PIN entered twice.
 *  2. A recovery code is shown once; the admin must tick "I have written this down".
 *     The admin and the recovery code hash are saved together at this point.
 *  3. Reminder to check the tablet's date and time, then go to Login.
 */
class SetupViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    // Kept out of the UI state so the PIN never reaches the UI layer.
    private var pin = ""
    private var firstPin = ""
    private var recoveryCode = ""

    fun onNameChange(name: String) = _state.update { it.copy(name = name, error = null) }

    fun onNameNext() {
        if (_state.value.name.isBlank()) {
            _state.update { it.copy(error = "Enter the admin's name.") }
            return
        }
        _state.update { it.copy(step = SetupStep.PIN, error = null) }
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
            SetupStep.PIN -> {
                PinRules.formatError(pin)?.let { msg -> _state.update { it.copy(error = msg) }; return }
                firstPin = pin
                pin = ""
                _state.update { it.copy(step = SetupStep.CONFIRM_PIN, pinLength = 0, error = null) }
            }
            SetupStep.CONFIRM_PIN -> {
                if (pin != firstPin) {
                    pin = ""
                    firstPin = ""
                    _state.update {
                        it.copy(
                            step = SetupStep.PIN,
                            pinLength = 0,
                            error = "The two PINs did not match. Choose your PIN again.",
                        )
                    }
                    return
                }
                recoveryCode = RecoveryCode.generate()
                _state.update {
                    it.copy(
                        step = SetupStep.RECOVERY_CODE,
                        pinLength = 0,
                        error = null,
                        recoveryCode = RecoveryCode.format(recoveryCode),
                    )
                }
            }
            else -> Unit
        }
    }

    /** Going back from the PIN steps returns to the name step and clears any typed PIN. */
    fun onBackToName() {
        pin = ""
        firstPin = ""
        _state.update { it.copy(step = SetupStep.NAME, pinLength = 0, error = null) }
    }

    fun onWrittenDownChange(checked: Boolean) = _state.update { it.copy(writtenDown = checked) }

    /** Saves the admin and recovery code hash, then shows the date/time reminder. */
    fun onRecoveryContinue() {
        val s = _state.value
        if (!s.writtenDown || s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                auth.completeSetup(s.name, pin, recoveryCode)
                // Forget the secrets as soon as they are saved.
                pin = ""
                firstPin = ""
                recoveryCode = ""
                _state.update {
                    it.copy(step = SetupStep.DATE_TIME, saving = false, recoveryCode = "")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(saving = false, error = "Could not save the admin account: ${e.message}")
                }
            }
        }
    }
}
