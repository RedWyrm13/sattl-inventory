package org.sattl.inventory.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec section 5.1: a 12-character recovery code. */
class RecoveryCodeTest {
    @Test
    fun generatesTwelveUnambiguousCharacters() {
        repeat(200) {
            val code = RecoveryCode.generate()
            assertEquals(12, code.length)
            assertTrue(code, code.none { it in "01OIL" })
            assertTrue(code, code.all { it.isUpperCase() || it.isDigit() })
        }
    }

    @Test
    fun formatsInGroupsOfFour() {
        assertEquals("ABCD-EFGH-JKMN", RecoveryCode.format("ABCDEFGHJKMN"))
    }

    @Test
    fun normalizeIgnoresDashesSpacesAndCase() {
        assertEquals("ABCDEFGHJKMN", RecoveryCode.normalize(" abcd-efgh jkmn "))
        val code = RecoveryCode.generate()
        assertEquals(code, RecoveryCode.normalize(RecoveryCode.format(code).lowercase()))
    }
}
