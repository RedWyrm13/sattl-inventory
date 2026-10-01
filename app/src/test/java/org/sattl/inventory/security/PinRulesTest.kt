package org.sattl.inventory.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec sections 5.2 and 8: PINs are 4 to 6 digits. */
class PinRulesTest {
    @Test
    fun acceptsFourToSixDigits() {
        assertTrue(PinRules.isValidFormat("0000"))
        assertTrue(PinRules.isValidFormat("12345"))
        assertTrue(PinRules.isValidFormat("999999"))
        assertNull(PinRules.formatError("1234"))
    }

    @Test
    fun rejectsWrongLengthOrNonDigits() {
        assertFalse(PinRules.isValidFormat(""))
        assertFalse(PinRules.isValidFormat("123"))
        assertFalse(PinRules.isValidFormat("1234567"))
        assertFalse(PinRules.isValidFormat("12a4"))
        assertFalse(PinRules.isValidFormat("12 34"))
        assertNotNull(PinRules.formatError("123"))
    }
}
