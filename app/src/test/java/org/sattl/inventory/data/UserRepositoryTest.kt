package org.sattl.inventory.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.sattl.inventory.data.db.AppDatabase
import org.sattl.inventory.data.entity.Role
import org.sattl.inventory.data.repo.AuthRepository
import org.sattl.inventory.data.repo.CheckoutRepository
import org.sattl.inventory.data.repo.CheckoutResult
import org.sattl.inventory.data.repo.ItemRepository
import org.sattl.inventory.data.repo.ItemSaveResult
import org.sattl.inventory.data.repo.LoginResult
import org.sattl.inventory.data.repo.UserRepository
import org.sattl.inventory.data.repo.UserSaveResult
import org.sattl.inventory.domain.CheckoutInput
import org.sattl.inventory.domain.ItemInput
import org.sattl.inventory.security.PinHasher
import org.sattl.inventory.security.RecoveryCode
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.util.today
import java.time.LocalDate

/** Manage users (spec 5.9) and rules 6.10–6.13 on a real Room database. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class UserRepositoryTest {

    private lateinit var db: AppDatabase
    private val clock = FakeClock()
    private lateinit var auth: AuthRepository
    private lateinit var repo: UserRepository
    private lateinit var admin: SessionUser

    @Before
    fun setUp() = runTest {
        db = newTestDb()
        auth = AuthRepository(db, clock)
        repo = UserRepository(db, clock)
        val a = auth.completeSetup("Pat", "2468", RecoveryCode.generate())
        admin = SessionUser(a.id, a.name, a.role)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(name: String, pin: String = "1111"): SessionUser {
        val u = (repo.addUser(admin, name, pin) as UserSaveResult.Saved).user
        return SessionUser(u.id, u.name, u.role)
    }

    @Test
    fun addUserCreatesRegularUserWhoMustChangePin() = runTest {
        val sam = add("  Sam Ortiz ", "1111")
        val stored = db.userDao().getById(sam.id)!!
        assertEquals("Sam Ortiz", stored.name)
        assertEquals(Role.USER, stored.role)
        assertTrue(stored.mustChangePin)
        assertTrue(stored.isActive)
        assertTrue(PinHasher.verify("1111", stored.pinHash, stored.pinSalt))
        assertFalse(stored.pinHash.contains("1111"))

        // They can log in with the default PIN and are told to change it (spec 5.2 step 3).
        val login = auth.login(sam.id, "1111") as LoginResult.Success
        assertTrue(login.user.mustChangePin)
    }

    @Test
    fun namesAreUniqueIgnoringCaseAndSpacesIncludingAdminAndInactive() = runTest {
        val sam = add("Sam")
        assertTrue(repo.addUser(admin, " sam ", "1111") is UserSaveResult.Invalid)
        assertTrue(repo.addUser(admin, "PAT", "1111") is UserSaveResult.Invalid)

        repo.setActive(admin, sam.id, false)
        val result = repo.addUser(admin, "SAM", "1111") as UserSaveResult.Invalid
        assertTrue(result.message, result.message.contains("reactivate"))
    }

    @Test
    fun addUserRejectsBlankNameAndBadPin() = runTest {
        assertTrue(repo.addUser(admin, "  ", "1111") is UserSaveResult.Invalid)
        assertTrue(repo.addUser(admin, "Sam", "12") is UserSaveResult.Invalid)
        assertTrue(repo.addUser(admin, "Sam", "12ab") is UserSaveResult.Invalid)
    }

    @Test
    fun onlyEverOneAdmin() = runTest {
        add("Sam")
        add("Alex")
        assertEquals(1, db.userDao().countAdmins())
    }

    @Test(expected = IllegalStateException::class)
    fun nonAdminCannotManageUsers() = runTest {
        val sam = add("Sam")
        repo.addUser(sam, "Alex", "1111")
    }

    @Test
    fun renameKeepsOwnNameButNotSomeoneElses() = runTest {
        val sam = add("Sam")
        add("Alex")
        assertTrue(repo.rename(admin, sam.id, "sam") is UserSaveResult.Saved)
        assertEquals("sam", db.userDao().getById(sam.id)!!.name)
        assertTrue(repo.rename(admin, sam.id, "alex") is UserSaveResult.Invalid)
        assertTrue(repo.rename(admin, admin.id, "Pat Lee") is UserSaveResult.Saved)
    }

    @Test
    fun resetPinSetsNewDefaultForcesChangeAndClearsLockout() = runTest {
        val sam = add("Sam", "1111")
        auth.changePin(sam.id, null, "5555")
        repeat(5) { auth.login(sam.id, "0000") }
        assertTrue(auth.login(sam.id, "5555") is LoginResult.LockedOut)

        assertTrue(repo.resetPin(admin, sam.id, "7777") is UserSaveResult.Saved)
        val u = db.userDao().getById(sam.id)!!
        assertTrue(u.mustChangePin)
        assertNull(u.lockedUntil)
        assertEquals(0, u.failedAttempts)

        assertEquals(LoginResult.WrongPin(4), auth.login(sam.id, "5555"))
        assertTrue(auth.login(sam.id, "7777") is LoginResult.Success)
    }

    @Test
    fun resetPinIsNotForTheAdminAccount() = runTest {
        assertTrue(repo.resetPin(admin, admin.id, "1111") is UserSaveResult.Invalid)
        assertTrue(auth.login(admin.id, "2468") is LoginResult.Success)
    }

    @Test
    fun adminCannotBeDeactivated() = runTest {
        assertTrue(repo.setActive(admin, admin.id, false) is UserSaveResult.Invalid)
        assertTrue(db.userDao().getById(admin.id)!!.isActive)
    }

    @Test
    fun userHoldingItemsCannotBeDeactivatedUntilCheckedIn() = runTest {
        val sam = add("Sam")
        val items = ItemRepository(db, clock)
        val checkouts = CheckoutRepository(db, clock)
        val itemId = (items.save(
            admin, null,
            ItemInput("T-1", "Ettus", "B210", "", "Room 214", LocalDate.of(2026, 1, 1)),
        ) as ItemSaveResult.Saved).itemId
        val c = checkouts.checkOut(sam, itemId, CheckoutInput(clock.today(), null, "Hangar", "Test")) as CheckoutResult.Done

        assertEquals(1, repo.observeUsers().first().single { it.user.id == sam.id }.openCheckouts)
        val blocked = repo.setActive(admin, sam.id, false) as UserSaveResult.Invalid
        assertTrue(blocked.message, blocked.message.contains("1 item"))

        checkouts.checkIn(admin, c.checkoutId)
        assertTrue(repo.setActive(admin, sam.id, false) is UserSaveResult.Saved)
        // History kept (rule 6.11).
        assertEquals("Sam", items.observeHistory(itemId).first().single().borrowerName)
    }

    @Test
    fun deactivatedUsersLeaveLoginListAndCanBeReactivated() = runTest {
        val sam = add("Sam")
        repo.setActive(admin, sam.id, false)
        assertEquals(listOf("Pat"), auth.activeUsers.first().map { it.name })
        assertEquals(LoginResult.UserNotFound, auth.login(sam.id, "1111"))
        // Still listed for the admin (spec section 3).
        assertEquals(listOf("Pat", "Sam"), repo.observeUsers().first().map { it.user.name })

        repo.setActive(admin, sam.id, true)
        assertEquals(listOf("Pat", "Sam"), auth.activeUsers.first().map { it.name })
        assertTrue(auth.login(sam.id, "1111") is LoginResult.Success)
    }
}
