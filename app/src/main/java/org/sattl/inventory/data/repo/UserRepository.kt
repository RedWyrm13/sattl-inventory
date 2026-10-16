package org.sattl.inventory.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User
import org.sattl.inventory.data.model.UserRow
import org.sattl.inventory.security.PinHasher
import org.sattl.inventory.security.PinRules
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.Clock

sealed interface UserSaveResult {
    data class Saved(val user: User) : UserSaveResult
    /** [message] says what is wrong and what to do (spec section 10). */
    data class Invalid(val message: String) : UserSaveResult
}

/**
 * Manage users, admin only (spec 5.9, rules 6.10–6.13). Every method takes the acting
 * [SessionUser] and refuses non-admins, as a safety net behind the UI (spec section 3).
 * Users are never deleted; deactivating hides them from login and keeps their history (6.11).
 */
class UserRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.SYSTEM,
) {
    private val users = db.userDao()

    fun observeUsers(): Flow<List<UserRow>> = users.observeUserRows()

    suspend fun getUser(id: Long): User? = users.getById(id)

    /**
     * Rule 6.13: names are unique ignoring case and surrounding spaces, across all users
     * including deactivated ones. Returns a plain-language error, or null if [name] is fine.
     */
    suspend fun nameError(name: String, editingId: Long? = null): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Enter the person's name."
        val other = users.findByName(n) ?: return null
        if (other.id == editingId) return null
        val inactive = if (other.isActive) "" else " (deactivated; you can reactivate them instead)"
        return "There is already a user called \"${other.name}\"$inactive. Use a different name."
    }

    /**
     * Spec 5.9: adds a regular user with a default PIN typed by the admin. mustChangePin is
     * true, so they must choose their own PIN at first login. There is only ever one admin
     * (spec section 3), so new users are always USER.
     */
    suspend fun addUser(actor: SessionUser, name: String, defaultPin: String): UserSaveResult {
        requireAdmin(actor)
        nameError(name)?.let { return UserSaveResult.Invalid(it) }
        PinRules.formatError(defaultPin)?.let { return UserSaveResult.Invalid(it) }
        val hashed = withContext(Dispatchers.Default) { PinHasher.hash(defaultPin) }
        return try {
            db.withTransaction {
                // Re-check inside the transaction (rule 6.15).
                nameError(name)?.let { return@withTransaction UserSaveResult.Invalid(it) }
                val user = User(
                    name = name.trim(),
                    role = Role.USER,
                    pinHash = hashed.hash,
                    pinSalt = hashed.salt,
                    mustChangePin = true,
                    createdAt = clock.now(),
                )
                UserSaveResult.Saved(user.copy(id = users.insert(user)))
            }
        } catch (_: SQLiteConstraintException) {
            UserSaveResult.Invalid("That name is already in use. Use a different name.")
        }
    }

    /** Spec 5.9: edit a user's name (the admin's own name included). */
    suspend fun rename(actor: SessionUser, userId: Long, name: String): UserSaveResult {
        requireAdmin(actor)
        return try {
            db.withTransaction {
                nameError(name, userId)?.let { return@withTransaction UserSaveResult.Invalid(it) }
                val user = users.getById(userId) ?: return@withTransaction UserSaveResult.Invalid("User not found.")
                val renamed = user.copy(name = name.trim())
                users.update(renamed)
                UserSaveResult.Saved(renamed)
            }
        } catch (_: SQLiteConstraintException) {
            UserSaveResult.Invalid("That name is already in use. Use a different name.")
        }
    }

    /**
     * Spec 5.9: the admin sets a new default PIN; mustChangePin becomes true and any lockout
     * is cleared. Not used for the admin's own account: the admin uses Change PIN instead,
     * because a forced change would be pointless when they just chose the PIN themselves.
     */
    suspend fun resetPin(actor: SessionUser, userId: Long, defaultPin: String): UserSaveResult {
        requireAdmin(actor)
        PinRules.formatError(defaultPin)?.let { return UserSaveResult.Invalid(it) }
        val hashed = withContext(Dispatchers.Default) { PinHasher.hash(defaultPin) }
        return db.withTransaction {
            val user = users.getById(userId) ?: return@withTransaction UserSaveResult.Invalid("User not found.")
            if (user.role == Role.ADMIN) {
                return@withTransaction UserSaveResult.Invalid("To change the admin PIN, use Change PIN at the top of the screen.")
            }
            val reset = user.copy(
                pinHash = hashed.hash,
                pinSalt = hashed.salt,
                mustChangePin = true,
                failedAttempts = 0,
                lockedUntil = null,
            )
            users.update(reset)
            UserSaveResult.Saved(reset)
        }
    }

    /**
     * Deactivate or reactivate (spec 5.9). Rule 6.12: the admin cannot be deactivated.
     * Rule 6.10: someone holding items cannot be deactivated until those items are checked in.
     * Rule 6.11: history is kept and the user can be reactivated.
     */
    suspend fun setActive(actor: SessionUser, userId: Long, active: Boolean): UserSaveResult {
        requireAdmin(actor)
        return db.withTransaction {
            val user = users.getById(userId) ?: return@withTransaction UserSaveResult.Invalid("User not found.")
            if (!active) {
                if (user.role == Role.ADMIN) {
                    return@withTransaction UserSaveResult.Invalid("The admin account cannot be deactivated.")
                }
                val held = users.countOpenCheckouts(userId)
                if (held > 0) {
                    val items = if (held == 1) "1 item" else "$held items"
                    return@withTransaction UserSaveResult.Invalid(
                        "${user.name} still has $items checked out. Check those items in first, then deactivate."
                    )
                }
            }
            val updated = user.copy(isActive = active)
            users.update(updated)
            UserSaveResult.Saved(updated)
        }
    }

    private fun requireAdmin(actor: SessionUser) {
        check(actor.isAdmin) { "Only the admin can manage users." }
    }
}
