package org.sattl.inventory.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec section 4: status is derived, in a fixed order of precedence. */
class ItemStatusTest {
    @Test
    fun availableByDefault() {
        assertEquals(ItemStatus.AVAILABLE, ItemStatus.of(testItem("A"), hasOpenCheckout = false))
    }

    @Test
    fun notCheckoutableWhenFlagOff() {
        assertEquals(ItemStatus.NOT_CHECKOUTABLE, ItemStatus.of(testItem("A", checkoutable = false), false))
    }

    @Test
    fun checkedOutBeatsNotCheckoutable() {
        // Rule 6.7: turning off checkoutable does not change an open checkout.
        assertEquals(ItemStatus.CHECKED_OUT, ItemStatus.of(testItem("A", checkoutable = false), true))
    }

    @Test
    fun retiredBeatsEverything() {
        assertEquals(ItemStatus.RETIRED, ItemStatus.of(testItem("A", retired = true, checkoutable = false), true))
    }

    @Test
    fun currentLocationIsDestinationWhileOutElseHome() {
        assertEquals("Room 214", testRow(testItem("A")).currentLocation)
        assertEquals("Field site", testRow(testItem("A"), borrower = sam).currentLocation)
    }
}
