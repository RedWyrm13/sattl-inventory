package org.sattl.inventory.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 5.5 button rules: who sees Check out and Check in. */
class ItemActionsTest {
    @Test
    fun onlyAvailableItemsCanBeCheckedOut() {
        assertTrue(ItemActions.canCheckOut(testRow(testItem("A"))))
        assertFalse(ItemActions.canCheckOut(testRow(testItem("A"), borrower = sam)))
        assertFalse(ItemActions.canCheckOut(testRow(testItem("A", checkoutable = false))))
        assertFalse(ItemActions.canCheckOut(testRow(testItem("A", retired = true))))
    }

    @Test
    fun borrowerAndAdminCanCheckInOthersCannot() {
        val outToSam = testRow(testItem("A"), borrower = sam)
        assertTrue(ItemActions.canCheckIn(outToSam, sam))
        assertTrue(ItemActions.canCheckIn(outToSam, admin))
        assertFalse(ItemActions.canCheckIn(outToSam, alex))
    }

    @Test
    fun nothingToCheckInWhenNotOut() {
        assertFalse(ItemActions.canCheckIn(testRow(testItem("A")), admin))
    }
}
