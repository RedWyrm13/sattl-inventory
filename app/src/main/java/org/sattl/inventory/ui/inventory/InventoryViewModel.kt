package org.sattl.inventory.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.sattl.inventory.data.model.InventoryRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sattl.inventory.data.backup.ViewCsv
import org.sattl.inventory.data.repo.BackupRepository
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.domain.BackupReminder
import org.sattl.inventory.util.Clock
import java.io.OutputStream
import java.time.ZoneId
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
    /** Admin only: overdue-backup banner text (spec 7.1), or null. */
    val backupReminder: String? = null,
    /** Result of the last "Export this view", or null. */
    val message: String? = null,
)

/** Inventory home screen (spec 5.4): search box, filter chips, item list, admin exports. */
class InventoryViewModel(
    items: ItemRepository,
    backups: BackupRepository,
    private val user: SessionUser,
    private val clock: Clock = Clock.SYSTEM,
) : ViewModel() {

    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow(InventoryFilter.ALL)
    private val message = MutableStateFlow<String?>(null)

    // Spec 7.1: only the admin sees the backup reminder.
    private val reminder = if (user.isAdmin) backups.lastExportAt.map { BackupReminder.message(it, clock.now()) } else flowOf(null)

    val state: StateFlow<InventoryUiState> =
        combine(items.observeInventory(), search, filter, reminder, message) { all, q, f, r, msg ->
            val filters = InventoryFilter.visibleTo(user)
            InventoryUiState(
                loading = false,
                search = q,
                filter = f,
                filters = filters,
                counts = filters.associateWith { InventoryQuery.apply(all, it, q, user).size },
                rows = InventoryQuery.apply(all, f, q, user),
                inventoryEmpty = all.none { user.isAdmin || !it.item.isRetired },
                backupReminder = r,
                message = msg,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun onSearchChange(text: String) {
        search.value = text
    }

    fun onFilterChange(f: InventoryFilter) {
        if (f in InventoryFilter.visibleTo(user)) filter.value = f
    }

    fun viewFileName(): String = ViewCsv.fileName(filter.value, clock.now(), ZoneId.systemDefault())

    /** Spec 7.2, admin only: writes exactly the rows on screen, with the current filter's columns. */
    fun exportView(fileName: String?, open: () -> OutputStream) {
        if (!user.isAdmin) return
        val s = state.value
        viewModelScope.launch {
            message.value = try {
                val csv = ViewCsv.write(s.rows, s.filter)
                withContext(Dispatchers.IO) { open().use { it.write(csv.toByteArray(Charsets.UTF_8)) } }
                val count = if (s.rows.size == 1) "1 item" else "${s.rows.size} items"
                "Saved $count to ${fileName ?: "the chosen file"}."
            } catch (e: Exception) {
                "The file could not be saved: ${e.message ?: "unknown error"}. Check the USB drive and try again."
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }
}
