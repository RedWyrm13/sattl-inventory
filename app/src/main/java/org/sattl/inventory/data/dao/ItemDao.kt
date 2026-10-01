package org.sattl.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.sattl.inventory.data.entity.Item
import org.sattl.inventory.data.model.InventoryRow

@Dao
interface ItemDao {
    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): Item?

    /** NOCASE collation on sattlTag makes this lookup case-insensitive (rule 6.13). */
    @Query("SELECT * FROM items WHERE sattlTag = :tag")
    suspend fun findByTag(tag: String): Item?

    /**
     * Every item (retired included) with its open checkout and borrower, sorted by SATTL tag
     * (spec 5.4). Filtering and search happen in [org.sattl.inventory.domain.InventoryQuery].
     */
    @Query(
        """
        SELECT items.*,
               c.id AS openCheckoutId, c.userId AS borrowerId, u.name AS borrowerName,
               c.checkoutDate AS checkoutDate, c.expectedReturnDate AS expectedReturnDate,
               c.destination AS destination, c.reason AS reason
        FROM items
        LEFT JOIN checkouts c ON c.itemId = items.id AND c.checkedInAt IS NULL
        LEFT JOIN users u ON u.id = c.userId
        ORDER BY items.sattlTag
        """
    )
    fun observeInventory(): Flow<List<InventoryRow>>

    /** One item with its open checkout, for Item detail (spec 5.5). */
    @Query(
        """
        SELECT items.*,
               c.id AS openCheckoutId, c.userId AS borrowerId, u.name AS borrowerName,
               c.checkoutDate AS checkoutDate, c.expectedReturnDate AS expectedReturnDate,
               c.destination AS destination, c.reason AS reason
        FROM items
        LEFT JOIN checkouts c ON c.itemId = items.id AND c.checkedInAt IS NULL
        LEFT JOIN users u ON u.id = c.userId
        WHERE items.id = :id
        """
    )
    fun observeInventoryRow(id: Long): Flow<InventoryRow?>
}
