package org.sattl.inventory.domain

import org.sattl.inventory.data.entity.Item

/** An item's status, derived from its fields and whether it has an open checkout (spec section 4). */
enum class ItemStatus(val label: String) {
    AVAILABLE("Available"),
    CHECKED_OUT("Checked out"),
    NOT_CHECKOUTABLE("Not checkoutable"),
    RETIRED("Retired");

    companion object {
        /**
         * Spec section 4, in this order: Retired if retired; otherwise Checked out if it has an
         * open checkout; otherwise Not checkoutable if isCheckoutable is false; otherwise Available.
         */
        fun of(item: Item, hasOpenCheckout: Boolean): ItemStatus = when {
            item.isRetired -> RETIRED
            hasOpenCheckout -> CHECKED_OUT
            !item.isCheckoutable -> NOT_CHECKOUTABLE
            else -> AVAILABLE
        }
    }
}
