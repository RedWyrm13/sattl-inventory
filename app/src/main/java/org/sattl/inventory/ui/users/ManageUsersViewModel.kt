package org.sattl.inventory.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.model.UserRow
import org.sattl.inventory.data.repo.UserRepository
import org.sattl.inventory.data.repo.UserSaveResult
import org.sattl.inventory.session.SessionManager
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.Clock

/** The Edit name dialog's state. */
data class RenameState(val userId: Long, val oldName: String, val text: String, val error: String? = null)

data class ManageUsersUiState(
    val loading: Boolean = true,
    val rows: List<UserRow> = emptyList(),
    /** For showing "Locked" in the list; refreshed every few seconds. */
    val now: Long = 0,
    val rename: RenameState? = null,
    /** Error or confirmation shown above the list. */
    val message: String? = null,
    val busy: Boolean = false,
)

/** Manage users (spec 5.9): list, Edit name, Deactivate / Reactivate. Add and Reset PIN have their own screen. */
class ManageUsersViewModel(
    private val users: UserRepository,
    private val session: SessionManager,
    private val actor: SessionUser,
    private val clock: Clock = Clock.SYSTEM,
) : ViewModel() {

    private val local = MutableStateFlow(ManageUsersUiState())
    private val ticker = flow {
        while (true) {
            emit(clock.now())
            delay(5_000)
        }
    }

    val state: StateFlow<ManageUsersUiState> = combine(users.observeUsers(), ticker, local) { rows, now, l ->
        l.copy(loading = false, rows = rows, now = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUsersUiState())

    fun startRename(row: UserRow) =
        local.update { it.copy(rename = RenameState(row.user.id, row.user.name, row.user.name), message = null) }

    fun onRenameText(text: String) = local.update { it.copy(rename = it.rename?.copy(text = text, error = null)) }

    fun cancelRename() = local.update { it.copy(rename = null) }

    fun saveRename() {
        val r = local.value.rename ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = users.rename(actor, r.userId, r.text)) {
                is UserSaveResult.Saved -> {
                    // Keep the top bar right if the admin renamed themselves.
                    session.refresh(result.user)
                    local.update { it.copy(busy = false, rename = null, message = "Renamed ${r.oldName} to ${result.user.name}.") }
                }
                is UserSaveResult.Invalid -> local.update { it.copy(busy = false, rename = r.copy(error = result.message)) }
            }
        }
    }

    fun setActive(row: UserRow, active: Boolean) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = users.setActive(actor, row.user.id, active)
            local.update {
                it.copy(
                    busy = false,
                    message = when (result) {
                        is UserSaveResult.Saved ->
                            if (active) "${row.user.name} has been reactivated and appears in the login list again."
                            else "${row.user.name} has been deactivated. Their history is kept."
                        is UserSaveResult.Invalid -> result.message
                    },
                )
            }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }
}
