package org.sattl.inventory.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.domain.InventoryFilter
import org.sattl.inventory.domain.InventoryQuery
import org.sattl.inventory.session.SessionUser

data class InventoryUiState(
    val loading: Boolean = true,
    val search: String = "",
    val filter: InventoryFilter = InventoryFilter.ALL,
    val filters: List<InventoryFilter> = emptyList(),
    /** Number of items each filter would show with the current search. */
    val counts: Map<InventoryFilter, Int> = emptyMap(),
    val rows: List<InventoryRow> = emptyList(),
    /** True when there are no (visible) items at all, as opposed to none matching. */
    val inventoryEmpty: Boolean = false,
)

/** Inventory home screen (spec 5.4): search box, filter chips, item list. */
class InventoryViewModel(
    items: ItemRepository,
    private val user: SessionUser,
) : ViewModel() {

    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow(InventoryFilter.ALL)

    val state: StateFlow<InventoryUiState> =
        combine(items.observeInventory(), search, filter) { all, q, f ->
            val filters = InventoryFilter.visibleTo(user)
            InventoryUiState(
                loading = false,
                search = q,
                filter = f,
                filters = filters,
                counts = filters.associateWith { InventoryQuery.apply(all, it, q, user).size },
                rows = InventoryQuery.apply(all, f, q, user),
                inventoryEmpty = all.none { user.isAdmin || !it.item.isRetired },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun onSearchChange(text: String) {
        search.value = text
    }

    fun onFilterChange(f: InventoryFilter) {
        if (f in InventoryFilter.visibleTo(user)) filter.value = f
    }
}
