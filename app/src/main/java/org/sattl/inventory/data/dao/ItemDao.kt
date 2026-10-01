package org.sattl.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.sattl.inventory.data.entity.Item

/** Item queries. Search/filter queries are added in milestone 2. */
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
}
