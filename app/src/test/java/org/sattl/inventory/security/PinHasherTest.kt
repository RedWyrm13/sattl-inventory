package org.sattl.inventory.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Spec section 8: salted PBKDF2 hashing of PINs and the recovery code. */
class PinHasherTest {

    @Test
    fun correctPinVerifies() {
        val h = PinHasher.hash("4821")
        assertTrue(PinHasher.verify("4821", h.hash, h.salt))
    }

    @Test
    fun wrongPinDoesNotVerify() {
        val h = PinHasher.hash("4821")
        assertFalse(PinHasher.verify("4822", h.hash, h.salt))
        assertFalse(PinHasher.verify("48210", h.hash, h.salt))
    }

    @Test
    fun saltIs16RandomBytesAndDiffersPerHash() {
        val a = PinHasher.hash("1234")
        val b = PinHasher.hash("1234")
        assertEquals(16, Base64.getDecoder().decode(a.salt).size)
        // Same PIN, different salt -> different hash (no lookup tables).
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun hashNeverContainsThePlainPin() {
        val h = PinHasher.hash("987654")
        assertFalse(h.hash.contains("987654"))
        assertFalse(h.salt.contains("987654"))
    }

    @Test
    fun iterationCountMeetsSpecMinimum() {
        assertTrue(PinHasher.ITERATIONS >= 100_000)
    }
}
