package org.sattl.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.sattl.inventory.data.entity.Checkout

/** Checkout queries. Check-out / check-in flows are added in milestone 3. */
@Dao
interface CheckoutDao {
    @Insert
    suspend fun insert(checkout: Checkout): Long

    @Update
    suspend fun update(checkout: Checkout)

    /** The open checkout for an item, if any. There can be at most one (rule 6.1). */
    @Query("SELECT * FROM checkouts WHERE itemId = :itemId AND checkedInAt IS NULL")
    suspend fun openCheckoutForItem(itemId: Long): Checkout?
}
