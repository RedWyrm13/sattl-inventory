package org.sattl.inventory.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Spec section 4 field rules for items. */
class ItemValidatorTest {
    private val valid = ItemInput(
        sattlTag = "T-1", manufacturer = "Ettus", modelNumber = "B210",
        homeLocation = "Room 214", dateIntoInventory = LocalDate.of(2026, 10, 1),
    )

    @Test
    fun validInputHasNoErrors() {
        assertTrue(ItemValidator.validate(valid).isEmpty())
    }

    @Test
    fun serialAndNotesAreOptional() {
        assertTrue(ItemValidator.validate(valid.copy(serialNumber = "", notes = "")).isEmpty())
    }

    @Test
    fun requiredFieldsMustNotBeBlank() {
        val errors = ItemValidator.validate(
            ItemInput(sattlTag = "  ", manufacturer = "", modelNumber = " ", homeLocation = "", dateIntoInventory = null)
        )
        assertEquals(
            setOf(ItemField.SATTL_TAG, ItemField.MANUFACTURER, ItemField.MODEL_NUMBER, ItemField.HOME_LOCATION, ItemField.DATE_INTO_INVENTORY),
            errors.keys,
        )
    }
}
