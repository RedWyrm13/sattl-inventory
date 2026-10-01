package org.sattl.inventory.domain

import java.time.LocalDate

/** The editable fields of the Add / Edit item form (spec 5.8). */
enum class ItemField { SATTL_TAG, MANUFACTURER, MODEL_NUMBER, SERIAL_NUMBER, HOME_LOCATION, DATE_INTO_INVENTORY, NOTES }

/** Raw values as typed on the form. */
data class ItemInput(
    val sattlTag: String = "",
    val manufacturer: String = "",
    val modelNumber: String = "",
    val serialNumber: String = "",
    val homeLocation: String = "",
    val dateIntoInventory: LocalDate? = null,
    val notes: String = "",
    val isCheckoutable: Boolean = true,
) {
    /** Spaces trimmed from every text field (rule 6.13 compares tags after trimming). */
    fun trimmed(): ItemInput = copy(
        sattlTag = sattlTag.trim(),
        manufacturer = manufacturer.trim(),
        modelNumber = modelNumber.trim(),
        serialNumber = serialNumber.trim(),
        homeLocation = homeLocation.trim(),
        notes = notes.trim(),
    )
}

/** Field rules from spec section 4 ("Item"). Duplicate-tag checking needs the database, so it lives in ItemRepository. */
object ItemValidator {
    /** Plain-language error per field (spec section 10); empty when everything is valid. */
    fun validate(input: ItemInput): Map<ItemField, String> {
        val i = input.trimmed()
        return buildMap {
            if (i.sattlTag.isEmpty()) put(ItemField.SATTL_TAG, "Enter the SATTL tag.")
            if (i.manufacturer.isEmpty()) put(ItemField.MANUFACTURER, "Enter the manufacturer.")
            if (i.modelNumber.isEmpty()) put(ItemField.MODEL_NUMBER, "Enter the model number.")
            if (i.homeLocation.isEmpty()) put(ItemField.HOME_LOCATION, "Enter where this item normally lives, e.g. \"Room 214\".")
            if (i.dateIntoInventory == null) put(ItemField.DATE_INTO_INVENTORY, "Choose the date the item entered inventory.")
        }
    }
}
