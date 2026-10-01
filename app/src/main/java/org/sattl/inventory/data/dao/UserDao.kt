package org.sattl.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.sattl.inventory.data.entity.User

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): User?

    /** NOCASE collation on name makes this lookup case-insensitive (rule 6.13). */
    @Query("SELECT * FROM users WHERE name = :name")
    suspend fun findByName(name: String): User?

    /** Login dropdown: active users only, alphabetical (spec section 5.2, rule 6.11). */
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY name")
    fun observeActiveUsers(): Flow<List<User>>

    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")
    suspend fun countAdmins(): Int
}
