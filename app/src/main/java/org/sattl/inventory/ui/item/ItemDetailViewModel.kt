package org.sattl.inventory.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.model.CheckoutHistoryRow
import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.data.repo.ItemActionResult
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.session.SessionUser

data class ItemDetailUiState(
    val loading: Boolean = true,
    /** Null if the item does not exist, or is retired and the user is not the admin (rule 6.9). */
    val row: InventoryRow? = null,
    /** Full history, admin only (spec section 3); always empty for other users. */
    val history: List<CheckoutHistoryRow> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
)

/** Item detail (spec 5.5). Check out / check in buttons are added in milestone 3. */
class ItemDetailViewModel(
    private val items: ItemRepository,
    private val user: SessionUser,
    private val itemId: Long,
) : ViewModel() {

    private val ui = MutableStateFlow(ItemDetailUiState())

    val state: StateFlow<ItemDetailUiState> = combine(
        items.observeItem(itemId),
        // Users never load history at all, not just never see it (spec section 3).
        if (user.isAdmin) items.observeHistory(itemId) else flowOf(emptyList()),
        ui,
    ) { row, history, local ->
        local.copy(
            loading = false,
            row = row?.takeIf { user.isAdmin || !it.item.isRetired },
            history = history,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    fun setRetired(retired: Boolean) = run { items.setRetired(user, itemId, retired) }

    fun setCheckoutable(checkoutable: Boolean) = run { items.setCheckoutable(user, itemId, checkoutable) }

    fun dismissMessage() = ui.update { it.copy(message = null) }

    private fun run(action: suspend () -> ItemActionResult) {
        if (ui.value.busy) return
        ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = action()
            ui.update {
                it.copy(busy = false, message = (result as? ItemActionResult.Blocked)?.message)
            }
        }
    }
}
