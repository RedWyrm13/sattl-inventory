package org.sattl.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.sattl.inventory.data.entity.Checkout
import org.sattl.inventory.data.model.CheckoutHistoryRow

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

    /** Full checkout history of one item, newest first, with names (admin only, spec 5.5). */
    @Query(
        """
        SELECT c.*, u.name AS borrowerName, ci.name AS checkedInByName
        FROM checkouts c
        JOIN users u ON u.id = c.userId
        LEFT JOIN users ci ON ci.id = c.checkedInByUserId
        WHERE c.itemId = :itemId
        ORDER BY c.createdAt DESC, c.id DESC
        """
    )
    fun observeHistory(itemId: Long): Flow<List<CheckoutHistoryRow>>
}
