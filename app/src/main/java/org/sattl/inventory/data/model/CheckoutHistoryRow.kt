package org.sattl.inventory.data.model

import androidx.room.Embedded
import org.sattl.inventory.data.entity.Checkout

/** One past or current checkout of an item, with names, for the admin history table (spec 5.5). */
data class CheckoutHistoryRow(
    @Embedded val checkout: Checkout,
    val borrowerName: String,
    /** Who checked it back in (borrower or admin, rule 6.4); null while still out. */
    val checkedInByName: String?,
)
