package org.sattl.inventory.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.entity.User
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.data.repo.ChangePinResult
import org.sattl.inventory.data.repo.LoginResult
import org.sattl.inventory.security.PinHasher
import org.sattl.inventory.security.PinRules
import org.sattl.inventory.security.RecoveryCode

/** Spec sections 5.1–5.3 and 8: setup, login lockout and PIN changes, on a real Room database. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AuthRepositoryTest {

    private lateinit var db: AppDatabase
    private val clock = FakeClock()
    private lateinit var repo: AuthRepository

    @Before
    fun setUp() {
        db = newTestDb()
        repo = AuthRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    /** Adds a regular user with an admin-assigned default PIN (as Manage users will in milestone 4). */
    private suspend fun addUser(name: String, defaultPin: String, active: Boolean = true): Long {
        val h = PinHasher.hash(defaultPin)
        return db.userDao().insert(
            User(
                name = name, role = Role.USER, pinHash = h.hash, pinSalt = h.salt,
                mustChangePin = true, isActive = active, createdAt = clock.now(),
            )
        )
    }

    // --- 5.1 First-time setup ---------------------------------------------------------------

    @Test
    fun setupCreatesAdminAndStoresOnlyHashes() = runTest {
        assertFalse(repo.setupComplete.first())
        val code = RecoveryCode.generate()

        val admin = repo.completeSetup("  Pat Lee  ", "2468", code)

        assertTrue(repo.setupComplete.first())
        val stored = db.userDao().getById(admin.id)!!
        assertEquals("Pat Lee", stored.name) // trimmed
        assertEquals(Role.ADMIN, stored.role)
        assertFalse(stored.mustChangePin)
        assertFalse(stored.pinHash.contains("2468"))
        assertTrue(PinHasher.verify("2468", stored.pinHash, stored.pinSalt))

        val settings = db.settingsDao().get()!!
        assertEquals(AppDatabase.VERSION, settings.schemaVersion)
        assertTrue(settings.keepScreenOn)
        assertTrue(PinHasher.verify(code, settings.recoveryCodeHash!!, settings.recoveryCodeSalt!!))
    }

    @Test(expected = IllegalStateException::class)
    fun setupCannotCreateASecondAdmin() = runTest {
        repo.completeSetup("Pat", "2468", RecoveryCode.generate())
        repo.completeSetup("Someone Else", "1357", RecoveryCode.generate())
    }

    @Test(expected = IllegalArgumentException::class)
    fun setupRejectsBadPin() = runTest {
        repo.completeSetup("Pat", "12", RecoveryCode.generate())
    }

    // --- 5.2 Login ----------------------------------------------------------------------------

    @Test
    fun loginListShowsOnlyActiveUsersAlphabetically() = runTest {
        repo.completeSetup("Pat", "2468", RecoveryCode.generate())
        addUser("zoe", "1111")
        addUser("Alex", "2222")
        addUser("Gone", "3333", active = false)

        assertEquals(listOf("Alex", "Pat", "zoe"), repo.activeUsers.first().map { it.name })
    }

    @Test
    fun correctPinLogsInAndReportsMustChangePin() = runTest {
        val id = addUser("Sam", "1111")
        val result = repo.login(id, "1111")
        assertTrue(result is LoginResult.Success)
        assertTrue((result as LoginResult.Success).user.mustChangePin)
    }

    @Test
    fun wrongPinCountsDownAttemptsAndCorrectPinResetsCounter() = runTest {
        val id = addUser("Sam", "1111")
        assertEquals(LoginResult.WrongPin(4), repo.login(id, "9999"))
        assertEquals(LoginResult.WrongPin(3), repo.login(id, "9999"))

        assertTrue(repo.login(id, "1111") is LoginResult.Success)
        assertEquals(0, db.userDao().getById(id)!!.failedAttempts)

        // Counter starts again from 5 after a successful login.
        assertEquals(LoginResult.WrongPin(4), repo.login(id, "9999"))
    }

    @Test
    fun fiveWrongPinsLockForFiveMinutesEvenWithCorrectPin() = runTest {
        val id = addUser("Sam", "1111")
        repeat(4) { repo.login(id, "9999") }
        val fifth = repo.login(id, "9999")
        assertEquals(LoginResult.LockedOut(clock.now() + PinRules.LOCKOUT_MILLIS), fifth)

        // Correct PIN is refused during the lockout.
        clock.advance(4 * 60 * 1000L)
        assertTrue(repo.login(id, "1111") is LoginResult.LockedOut)

        // After 5 minutes the correct PIN works and the lockout is cleared.
        clock.advance(60 * 1000L)
        assertTrue(repo.login(id, "1111") is LoginResult.Success)
        assertNull(db.userDao().getById(id)!!.lockedUntil)
    }

    @Test
    fun lockoutIsStoredInDatabaseSoItSurvivesRestart() = runTest {
        val id = addUser("Sam", "1111")
        repeat(5) { repo.login(id, "9999") }
        assertNotNull(db.userDao().getById(id)!!.lockedUntil)

        // A brand-new repository (as after an app restart) still sees the lockout.
        val afterRestart = AuthRepository(db, clock)
        assertTrue(afterRestart.login(id, "1111") is LoginResult.LockedOut)
    }

    @Test
    fun inactiveUserCannotLogIn() = runTest {
        val id = addUser("Gone", "1111", active = false)
        assertEquals(LoginResult.UserNotFound, repo.login(id, "1111"))
    }

    // --- 5.3 Change PIN -----------------------------------------------------------------------

    @Test
    fun forcedChangeRejectsTheDefaultPin() = runTest {
        val id = addUser("Sam", "1111")
        assertTrue(repo.changePin(id, null, "1111") is ChangePinResult.Invalid)
        assertTrue(db.userDao().getById(id)!!.mustChangePin)
    }

    @Test
    fun forcedChangeSetsNewPinAndClearsFlag() = runTest {
        val id = addUser("Sam", "1111")
        assertEquals(ChangePinResult.Success, repo.changePin(id, null, "5555"))

        val user = db.userDao().getById(id)!!
        assertFalse(user.mustChangePin)
        assertTrue(repo.login(id, "5555") is LoginResult.Success)
        assertEquals(LoginResult.WrongPin(4), repo.login(id, "1111"))
    }

    @Test
    fun voluntaryChangeRequiresCorrectCurrentPin() = runTest {
        val admin = repo.completeSetup("Pat", "2468", RecoveryCode.generate())

        assertTrue(repo.changePin(admin.id, "0000", "1357") is ChangePinResult.Invalid)
        assertTrue(repo.changePin(admin.id, null, "1357") is ChangePinResult.Invalid)
        assertTrue(repo.login(admin.id, "2468") is LoginResult.Success)

        assertEquals(ChangePinResult.Success, repo.changePin(admin.id, "2468", "1357"))
        assertTrue(repo.login(admin.id, "1357") is LoginResult.Success)
    }

    @Test
    fun changePinRejectsBadFormat() = runTest {
        val id = addUser("Sam", "1111")
        assertTrue(repo.changePin(id, null, "12") is ChangePinResult.Invalid)
        assertTrue(repo.changePin(id, null, "abcd") is ChangePinResult.Invalid)
    }
}
