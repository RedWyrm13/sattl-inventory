package org.sattl.inventory.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Spec 5.6 and section 4 rules for the Check out form. */
class CheckoutValidatorTest {
    private val today = LocalDate.of(2026, 10, 7)
    private val valid = CheckoutInput(checkoutDate = today, destination = "Hangar 3", reason = "Demo")

    private fun errors(input: CheckoutInput) = CheckoutValidator.validate(input, today).keys

    @Test
    fun validInputHasNoErrors() {
        assertTrue(errors(valid).isEmpty())
        assertTrue(errors(valid.copy(expectedReturnDate = today)).isEmpty())
        assertTrue(errors(valid.copy(checkoutDate = today.minusDays(3))).isEmpty())
    }

    @Test
    fun destinationAndReasonAreRequired() {
        assertEquals(
            setOf(CheckoutField.DESTINATION, CheckoutField.REASON),
            errors(valid.copy(destination = "  ", reason = "")),
        )
    }

    @Test
    fun checkoutDateRequiredAndNotInFuture() {
        assertEquals(setOf(CheckoutField.CHECKOUT_DATE), errors(valid.copy(checkoutDate = null)))
        assertEquals(setOf(CheckoutField.CHECKOUT_DATE), errors(valid.copy(checkoutDate = today.plusDays(1))))
    }

    @Test
    fun expectedReturnNotBeforeCheckoutDate() {
        assertEquals(
            setOf(CheckoutField.EXPECTED_RETURN_DATE),
            errors(valid.copy(expectedReturnDate = today.minusDays(1))),
        )
    }
}
