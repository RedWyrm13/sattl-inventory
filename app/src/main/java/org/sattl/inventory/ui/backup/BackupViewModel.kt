package org.sattl.inventory.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.backup.ReadResult
import org.sattl.inventory.data.backup.ValidBackup
import org.sattl.inventory.data.repo.BackupRepository
import org.sattl.inventory.data.repo.RestoreResult
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.components.plusPinDigit
import java.io.InputStream
import java.io.OutputStream

/** Where the Export / Restore screen is in its flow. */
sealed interface BackupStage {
    data object Idle : BackupStage
    data class Working(val message: String) : BackupStage
    data class ExportDone(val fileName: String?) : BackupStage
    data class Failed(val message: String) : BackupStage

    /** The chosen file is not a usable backup; nothing was changed (spec 7.3 step 3). */
    data class Invalid(val problems: List<String>) : BackupStage

    /** Valid backup: show the summary and ask for the admin's PIN (spec 7.3 step 4). */
    data class Confirm(val backup: ValidBackup, val pinLength: Int = 0, val error: String? = null, val busy: Boolean = false) : BackupStage

    /** Restored; the screen shows a message, then logs everyone out (spec 7.3 step 6). */
    data object Restored : BackupStage
}

data class BackupUiState(val stage: BackupStage = BackupStage.Idle, val lastExportAt: Long? = null)

/** Full export and restore (spec 7.1, 7.3), admin only. */
class BackupViewModel(
    private val backups: BackupRepository,
    private val actor: SessionUser,
) : ViewModel() {

    private val stage = MutableStateFlow<BackupStage>(BackupStage.Idle)

    val state: StateFlow<BackupUiState> = combine(stage, backups.lastExportAt) { s, last ->
        BackupUiState(s, last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    // The PIN typed to confirm a restore; never in UI state.
    private var pin = ""

    fun suggestedFileName(): String = backups.suggestedFileName()

    /** Writes the backup to the file the admin chose in the picker. */
    fun export(fileName: String?, open: () -> OutputStream) {
        stage.value = BackupStage.Working("Saving backup…")
        viewModelScope.launch {
            stage.value = try {
                backups.exportFull(actor, open())
                BackupStage.ExportDone(fileName)
            } catch (e: Exception) {
                BackupStage.Failed(
                    "The backup could not be saved: ${e.message ?: "unknown error"}. " +
                        "Check the USB drive is plugged in and has free space, then try again."
                )
            }
        }
    }

    /** Reads and validates the file the admin chose (spec 7.3 step 2). */
    fun read(open: () -> InputStream) {
        stage.value = BackupStage.Working("Checking backup file…")
        viewModelScope.launch {
            stage.value = try {
                when (val result = backups.read(open())) {
                    is ReadResult.Valid -> BackupStage.Confirm(result.backup)
                    is ReadResult.Invalid -> BackupStage.Invalid(result.problems)
                }
            } catch (e: Exception) {
                BackupStage.Failed("The file could not be opened: ${e.message ?: "unknown error"}.")
            }
        }
    }

    fun onDigit(d: Char) = updateConfirm { pin = pin.plusPinDigit(d); it.copy(pinLength = pin.length, error = null) }

    fun onBackspace() = updateConfirm { pin = pin.dropLast(1); it.copy(pinLength = pin.length) }

    /** Spec 7.3 steps 4–5: check the admin's current PIN, then replace all data. */
    fun confirmRestore() {
        val confirm = stage.value as? BackupStage.Confirm ?: return
        if (confirm.busy || pin.isEmpty()) return
        val typed = pin
        pin = ""
        stage.value = confirm.copy(busy = true, pinLength = 0)
        viewModelScope.launch {
            stage.value = when (val result = backups.restore(actor, typed, confirm.backup)) {
                RestoreResult.Done -> BackupStage.Restored
                is RestoreResult.Failed -> confirm.copy(busy = false, pinLength = 0, error = result.message)
            }
        }
    }

    fun reset() {
        pin = ""
        stage.value = BackupStage.Idle
    }

    private fun updateConfirm(change: (BackupStage.Confirm) -> BackupStage.Confirm) {
        val c = stage.value as? BackupStage.Confirm ?: return
        if (!c.busy) stage.update { change(c) }
    }
}
