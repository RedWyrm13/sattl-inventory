package org.sattl.inventory.domain

import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.session.SessionUser

/**
 * Who may check an item out or in. Used to decide which buttons Item detail shows
 * (spec 5.5); CheckoutRepository enforces the same rules again when saving.
 */
object ItemActions {
    /** Rule 6.1 / spec 5.5: only an Available item can be checked out, by anyone logged in. */
    fun canCheckOut(row: InventoryRow): Boolean = row.status == ItemStatus.AVAILABLE

    /** Rule 6.4 / spec 5.5: only the borrower or the admin can check an item in. */
    fun canCheckIn(row: InventoryRow, user: SessionUser): Boolean =
        row.status == ItemStatus.CHECKED_OUT && (row.borrowerId == user.id || user.isAdmin)
}
