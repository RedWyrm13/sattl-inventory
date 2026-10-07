package org.sattl.inventory.domain

import java.time.LocalDate

/** The fields of the Check out form (spec 5.6). */
enum class CheckoutField { CHECKOUT_DATE, EXPECTED_RETURN_DATE, DESTINATION, REASON }

data class CheckoutInput(
    val checkoutDate: LocalDate? = null,
    val expectedReturnDate: LocalDate? = null,
    val destination: String = "",
    val reason: String = "",
) {
    fun trimmed(): CheckoutInput = copy(destination = destination.trim(), reason = reason.trim())
}

/** Field rules from spec section 4 ("Checkout") and 5.6. */
object CheckoutValidator {
    /** Plain-language error per field (spec section 10); empty when valid. [today] is the tablet's date. */
    fun validate(input: CheckoutInput, today: LocalDate): Map<CheckoutField, String> {
        val i = input.trimmed()
        return buildMap {
            when {
                i.checkoutDate == null -> put(CheckoutField.CHECKOUT_DATE, "Choose the checkout date.")
                // A future checkout would be a reservation, which is out of scope (spec section 13).
                i.checkoutDate.isAfter(today) -> put(CheckoutField.CHECKOUT_DATE, "The checkout date cannot be in the future.")
            }
            // Spec section 4: expected return must not be before the checkout date. Optional and
            // informational only (rule 6.6).
            if (i.expectedReturnDate != null && i.checkoutDate != null && i.expectedReturnDate.isBefore(i.checkoutDate)) {
                put(CheckoutField.EXPECTED_RETURN_DATE, "The expected return date cannot be before the checkout date.")
            }
            if (i.destination.isEmpty()) put(CheckoutField.DESTINATION, "Enter where the item is going.")
            if (i.reason.isEmpty()) put(CheckoutField.REASON, "Enter why you are checking it out.")
        }
    }
}
