package org.sattl.inventory.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Spec section 3: exactly one ADMIN (the lab manager); everyone else is a USER. */
enum class Role { ADMIN, USER }

/**
 * A person who can log in (spec section 4, "User").
 *
 * The PIN is never stored; only a salted PBKDF2 hash (spec section 8).
 * Users are never hard-deleted; deactivating sets [isActive] to false (rule 6.11).
 */
@Entity(
    tableName = "users",
    // Rule 6.13: user names are unique, case-insensitive after trimming (NOCASE collation).
    indices = [Index(value = ["name"], unique = true)],
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val role: Role,
    /** Base64 PBKDF2-HMAC-SHA256 hash of the PIN. */
    val pinHash: String,
    /** Base64 random 16-byte salt for [pinHash]. */
    val pinSalt: String,
    /** True after the account is created or the admin resets the PIN (spec section 5.3). */
    val mustChangePin: Boolean,
    /** Consecutive wrong PINs. Reset to 0 on successful login (spec section 8). */
    val failedAttempts: Int = 0,
    /** UTC epoch ms until which login is blocked, or null. Stored so it survives restarts. */
    val lockedUntil: Long? = null,
    /** Inactive users are hidden from the login list (rule 6.11). */
    val isActive: Boolean = true,
    /** UTC epoch milliseconds. */
    val createdAt: Long,
)
