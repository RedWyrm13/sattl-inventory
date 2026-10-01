package org.sattl.inventory.data.model

import androidx.room.Embedded
import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.domain.ItemStatus
import java.time.LocalDate

/**
 * One item plus its open checkout (if any) and the borrower's name. Produced by
 * [org.sattl.inventory.data.dao.ItemDao.observeInventory]. The checkout fields are all null
 * when the item is not checked out.
 */
data class InventoryRow(
    @Embedded val item: Item,
    val openCheckoutId: Long?,
    val borrowerId: Long?,
    val borrowerName: String?,
    val checkoutDate: LocalDate?,
    val expectedReturnDate: LocalDate?,
    val destination: String?,
    val reason: String?,
) {
    val isCheckedOut: Boolean get() = openCheckoutId != null

    /** Derived, never stored (spec section 4). */
    val status: ItemStatus get() = ItemStatus.of(item, isCheckedOut)

    /** Spec section 4 / rule 6.5: the checkout destination while out, otherwise home location. */
    val currentLocation: String get() = if (isCheckedOut) destination ?: item.homeLocation else item.homeLocation
}
