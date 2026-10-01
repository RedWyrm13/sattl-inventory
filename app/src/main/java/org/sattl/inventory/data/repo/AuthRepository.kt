package org.sattl.inventory.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.AppSettings
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User
import org.sattl.inventory.security.PinHasher
import org.sattl.inventory.security.PinRules
import org.sattl.inventory.security.RecoveryCode
import org.sattl.inventory.util.Clock

/** Outcome of a login attempt (spec section 5.2). */
sealed interface LoginResult {
    data class Success(val user: User) : LoginResult
    data class WrongPin(val attemptsRemaining: Int) : LoginResult
    data class LockedOut(val lockedUntil: Long) : LoginResult
    data object UserNotFound : LoginResult
}

/** Outcome of a PIN change (spec section 5.3). */
sealed interface ChangePinResult {
    data object Success : ChangePinResult
    data class Invalid(val message: String) : ChangePinResult
}

/**
 * First-time setup, login with lockout, and PIN changes.
 *
 * Every write runs in a database transaction, so nothing is lost to a crash or power loss
 * (rule 6.15). PIN hashing is slow, so it runs on [Dispatchers.Default].
 */
class AuthRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.SYSTEM,
) {
    private val users = db.userDao()
    private val settings = db.settingsDao()

    /** Emits true once first-time setup has finished (spec section 5.1). */
    val setupComplete: Flow<Boolean> = settings.observe().map { it?.setupComplete == true }

    /** Active users for the login dropdown, alphabetical (spec section 5.2). */
    val activeUsers: Flow<List<User>> = users.observeActiveUsers()

    suspend fun getUser(id: Long): User? = users.getById(id)

    /**
     * Spec section 5.1: creates the one admin account and stores the recovery code hash.
     * Both are saved in a single transaction. Fails if setup has already been done, because
     * there can only be one admin (spec section 3).
     */
    suspend fun completeSetup(adminName: String, pin: String, recoveryCode: String): User {
        val name = adminName.trim()
        require(name.isNotEmpty()) { "Enter the admin's name." }
        PinRules.formatError(pin)?.let { throw IllegalArgumentException(it) }

        val pinHashed = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        val codeHashed = withContext(Dispatchers.Default) {
            PinHasher.hash(RecoveryCode.normalize(recoveryCode))
        }
        val now = clock.now()

        return db.withTransaction {
            check(settings.get()?.setupComplete != true && users.countAdmins() == 0) {
                "Setup has already been completed."
            }
            val admin = User(
                name = name,
                role = Role.ADMIN,
                pinHash = pinHashed.hash,
                pinSalt = pinHashed.salt,
                // The admin chose this PIN themselves, so no forced change.
                mustChangePin = false,
                createdAt = now,
            )
            val id = users.insert(admin)
            settings.upsert(
                AppSettings(
                    setupComplete = true,
                    recoveryCodeHash = codeHashed.hash,
                    recoveryCodeSalt = codeHashed.salt,
                    schemaVersion = AppDatabase.VERSION,
                )
            )
            admin.copy(id = id)
        }
    }

    /**
     * Spec sections 5.2 and 8:
     *  - A locked user cannot log in until lockedUntil passes, even with the right PIN.
     *  - Each wrong PIN increments failedAttempts. The 5th in a row locks the user for
     *    5 minutes and resets the counter, so they get 5 fresh tries after the lockout.
     *  - A correct PIN resets failedAttempts to 0 and clears any expired lockout.
     * The lockout is stored in the database, so it survives app restarts.
     */
    suspend fun login(userId: Long, pin: String): LoginResult {
        val user = users.getById(userId)
        if (user == null || !user.isActive) return LoginResult.UserNotFound

        val now = clock.now()
        user.lockedUntil?.let { if (it > now) return LoginResult.LockedOut(it) }

        val correct = PinRules.isValidFormat(pin) &&
            withContext(Dispatchers.Default) { PinHasher.verify(pin, user.pinHash, user.pinSalt) }

        return db.withTransaction {
            // Re-read inside the transaction so the counter is never based on stale data.
            val fresh = users.getById(userId) ?: return@withTransaction LoginResult.UserNotFound
            if (correct) {
                val updated = fresh.copy(failedAttempts = 0, lockedUntil = null)
                users.update(updated)
                LoginResult.Success(updated)
            } else {
                val attempts = fresh.failedAttempts + 1
                if (attempts >= PinRules.MAX_FAILED_ATTEMPTS) {
                    val until = now + PinRules.LOCKOUT_MILLIS
                    users.update(fresh.copy(failedAttempts = 0, lockedUntil = until))
                    LoginResult.LockedOut(until)
                } else {
                    users.update(fresh.copy(failedAttempts = attempts, lockedUntil = null))
                    LoginResult.WrongPin(PinRules.MAX_FAILED_ATTEMPTS - attempts)
                }
            }
        }
    }

    /**
     * Spec section 5.3.
     *  - Forced change (mustChangePin is true, right after login): [currentPin] is not needed,
     *    and the new PIN may not equal the default PIN the admin assigned.
     *  - Voluntary change from the top bar: [currentPin] is required and must be correct.
     * On success mustChangePin becomes false.
     */
    suspend fun changePin(userId: Long, currentPin: String?, newPin: String): ChangePinResult {
        val user = users.getById(userId) ?: return ChangePinResult.Invalid("User not found.")
        PinRules.formatError(newPin)?.let { return ChangePinResult.Invalid(it) }

        val matchesCurrent = { pin: String ->
            PinHasher.verify(pin, user.pinHash, user.pinSalt)
        }
        val error = withContext(Dispatchers.Default) {
            when {
                user.mustChangePin && matchesCurrent(newPin) ->
                    "Your new PIN must be different from the PIN the admin gave you."
                !user.mustChangePin && (currentPin == null || !matchesCurrent(currentPin)) ->
                    "Your current PIN is incorrect. Try again."
                else -> null
            }
        }
        if (error != null) return ChangePinResult.Invalid(error)

        val hashed = withContext(Dispatchers.Default) { PinHasher.hash(newPin) }
        db.withTransaction {
            val fresh = users.getById(userId) ?: return@withTransaction
            users.update(fresh.copy(pinHash = hashed.hash, pinSalt = hashed.salt, mustChangePin = false))
        }
        return ChangePinResult.Success
    }
}
