package org.sattl.inventory.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.data.repo.ItemSaveResult
import org.sattl.inventory.domain.ItemField
import org.sattl.inventory.domain.ItemInput
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.Clock
import org.sattl.inventory.util.today
import java.time.LocalDate

data class ItemFormUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val input: ItemInput = ItemInput(),
    val errors: Map<ItemField, String> = emptyMap(),
    val saving: Boolean = false,
    /** Set when editing an item that no longer exists. */
    val notFound: Boolean = false,
)

/** Add / edit item form, admin only (spec 5.8). [itemId] null means a new item. */
class ItemFormViewModel(
    private val items: ItemRepository,
    private val user: SessionUser,
    private val itemId: Long?,
    clock: Clock = Clock.SYSTEM,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemFormUiState(isNew = itemId == null))
    val state: StateFlow<ItemFormUiState> = _state.asStateFlow()

    /** Emits the item id once saved. */
    private val _saved = Channel<Long>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    init {
        if (itemId == null) {
            // Spec section 4: date into inventory defaults to today.
            _state.update { it.copy(loading = false, input = ItemInput(dateIntoInventory = clock.today())) }
        } else {
            viewModelScope.launch {
                val item = items.observeItem(itemId).first()?.item
                _state.update {
                    if (item == null) it.copy(loading = false, notFound = true)
                    else it.copy(
                        loading = false,
                        input = ItemInput(
                            sattlTag = item.sattlTag,
                            manufacturer = item.manufacturer,
                            modelNumber = item.modelNumber,
                            serialNumber = item.serialNumber.orEmpty(),
                            homeLocation = item.homeLocation,
                            dateIntoInventory = item.dateIntoInventory,
                            notes = item.notes.orEmpty(),
                            isCheckoutable = item.isCheckoutable,
                        ),
                    )
                }
            }
        }
    }

    /** Updates the form and clears the error for the field being edited. */
    fun onChange(field: ItemField?, transform: (ItemInput) -> ItemInput) {
        _state.update { s ->
            s.copy(input = transform(s.input), errors = if (field == null) s.errors else s.errors - field)
        }
    }

    fun onTagChange(v: String) = onChange(ItemField.SATTL_TAG) { it.copy(sattlTag = v) }
    fun onManufacturerChange(v: String) = onChange(ItemField.MANUFACTURER) { it.copy(manufacturer = v) }
    fun onModelChange(v: String) = onChange(ItemField.MODEL_NUMBER) { it.copy(modelNumber = v) }
    fun onSerialChange(v: String) = onChange(ItemField.SERIAL_NUMBER) { it.copy(serialNumber = v) }
    fun onHomeLocationChange(v: String) = onChange(ItemField.HOME_LOCATION) { it.copy(homeLocation = v) }
    fun onDateChange(v: LocalDate) = onChange(ItemField.DATE_INTO_INVENTORY) { it.copy(dateIntoInventory = v) }
    fun onNotesChange(v: String) = onChange(ItemField.NOTES) { it.copy(notes = v) }
    fun onCheckoutableChange(v: Boolean) = onChange(null) { it.copy(isCheckoutable = v) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading || s.notFound) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = items.save(user, itemId, s.input)) {
                is ItemSaveResult.Saved -> _saved.send(result.itemId)
                is ItemSaveResult.Invalid -> _state.update { it.copy(saving = false, errors = result.errors) }
            }
        }
    }
}
