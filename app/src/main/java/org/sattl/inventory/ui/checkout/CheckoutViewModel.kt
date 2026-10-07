package org.sattl.inventory.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.data.repo.CheckoutRepository
import org.sattl.inventory.data.repo.CheckoutResult
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.domain.CheckoutField
import org.sattl.inventory.domain.CheckoutInput
import org.sattl.inventory.domain.ItemActions
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.Clock
import org.sattl.inventory.util.today
import java.time.LocalDate

data class CheckoutUiState(
    val loading: Boolean = true,
    val row: InventoryRow? = null,
    val today: LocalDate = LocalDate.MIN,
    val input: CheckoutInput = CheckoutInput(),
    val errors: Map<CheckoutField, String> = emptyMap(),
    /** Why the item cannot be checked out (e.g. someone else just took it), or null. */
    val blocked: String? = null,
    val saving: Boolean = false,
    /** True once saved: the screen shows the success message, then logs out (spec 5.6). */
    val done: Boolean = false,
)

/** Check out form (spec 5.6). The borrower is always [user], the logged-in person (rule 6.3). */
class CheckoutViewModel(
    items: ItemRepository,
    private val checkouts: CheckoutRepository,
    private val user: SessionUser,
    private val itemId: Long,
    clock: Clock = Clock.SYSTEM,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    init {
        val today = clock.today()
        viewModelScope.launch {
            val row = items.observeItem(itemId).first()
            _state.update {
                it.copy(
                    loading = false,
                    row = row,
                    today = today,
                    // Spec 5.6: checkout date defaults to today.
                    input = CheckoutInput(checkoutDate = today),
                    blocked = when {
                        row == null || (row.item.isRetired && !user.isAdmin) -> "This item is not available."
                        !ItemActions.canCheckOut(row) -> "${row.item.sattlTag} is ${row.status.label.lowercase()}, so it cannot be checked out."
                        else -> null
                    },
                )
            }
        }
    }

    private fun edit(field: CheckoutField, transform: (CheckoutInput) -> CheckoutInput) =
        _state.update { it.copy(input = transform(it.input), errors = it.errors - field) }

    fun onCheckoutDateChange(d: LocalDate) = edit(CheckoutField.CHECKOUT_DATE) { it.copy(checkoutDate = d) }
    fun onExpectedReturnChange(d: LocalDate?) = edit(CheckoutField.EXPECTED_RETURN_DATE) { it.copy(expectedReturnDate = d) }
    fun onDestinationChange(v: String) = edit(CheckoutField.DESTINATION) { it.copy(destination = v) }
    fun onReasonChange(v: String) = edit(CheckoutField.REASON) { it.copy(reason = v) }

    fun confirm() {
        val s = _state.value
        if (s.loading || s.saving || s.done || s.blocked != null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = checkouts.checkOut(user, itemId, s.input)) {
                is CheckoutResult.Done -> _state.update { it.copy(saving = false, done = true) }
                is CheckoutResult.Invalid -> _state.update { it.copy(saving = false, errors = result.errors) }
                is CheckoutResult.Blocked -> _state.update { it.copy(saving = false, blocked = result.message) }
            }
        }
    }
}
