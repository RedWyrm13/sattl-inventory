package org.sattl.inventory.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sattl.inventory.session.SessionUser

/** Spec 5.4 filters and search; acceptance criteria "Search finds..." and "Each filter shows...". */
class InventoryQueryTest {

    private val available = testRow(testItem("A-1"))
    private val outToSam = testRow(testItem("A-2"), borrower = sam, destination = "Hangar 3")
    private val outToAlex = testRow(testItem("A-3"), borrower = alex)
    private val notCheckoutable = testRow(testItem("A-4", checkoutable = false))
    private val notCheckoutableButOut = testRow(testItem("A-5", checkoutable = false), borrower = sam)
    private val retired = testRow(testItem("A-6", retired = true))
    private val all = listOf(available, outToSam, outToAlex, notCheckoutable, notCheckoutableButOut, retired)

    private fun tags(filter: InventoryFilter, user: SessionUser = sam, search: String = "") =
        InventoryQuery.apply(all, filter, search, user).map { it.item.sattlTag }

    @Test
    fun allExcludesRetiredForEveryone() {
        assertEquals(listOf("A-1", "A-2", "A-3", "A-4", "A-5"), tags(InventoryFilter.ALL))
        assertEquals(listOf("A-1", "A-2", "A-3", "A-4", "A-5"), tags(InventoryFilter.ALL, admin))
    }

    @Test
    fun availableShowsOnlyAvailable() {
        assertEquals(listOf("A-1"), tags(InventoryFilter.AVAILABLE))
    }

    @Test
    fun checkedOutIncludesNotCheckoutableItemsThatAreOut() {
        assertEquals(listOf("A-2", "A-3", "A-5"), tags(InventoryFilter.CHECKED_OUT))
    }

    @Test
    fun myCheckoutsShowsOnlyTheCurrentUsersItems() {
        assertEquals(listOf("A-2", "A-5"), tags(InventoryFilter.MY_CHECKOUTS, sam))
        assertEquals(listOf("A-3"), tags(InventoryFilter.MY_CHECKOUTS, alex))
        assertEquals(emptyList<String>(), tags(InventoryFilter.MY_CHECKOUTS, admin))
    }

    @Test
    fun notCheckoutableShowsOnlyThatStatus() {
        assertEquals(listOf("A-4"), tags(InventoryFilter.NOT_CHECKOUTABLE))
    }

    @Test
    fun retiredIsAdminOnly() {
        assertEquals(listOf("A-6"), tags(InventoryFilter.RETIRED, admin))
        assertEquals(emptyList<String>(), tags(InventoryFilter.RETIRED, sam))
        assertFalse(InventoryFilter.RETIRED in InventoryFilter.visibleTo(sam))
        assertTrue(InventoryFilter.RETIRED in InventoryFilter.visibleTo(admin))
    }

    @Test
    fun searchMatchesEachFieldIgnoringCase() {
        val row = testRow(testItem("SATTL-042", manufacturer = "Dell", model = "R740", serial = "XK9", home = "Room 101"))
        fun hit(q: String) = InventoryQuery.matchesSearch(row, q)
        assertTrue(hit("sattl-04"))
        assertTrue(hit("DELL"))
        assertTrue(hit("r74"))
        assertTrue(hit("xk9"))
        assertTrue(hit("room 101"))
        assertTrue(hit("  dell  ")) // surrounding spaces ignored
        assertTrue(hit(""))
        assertFalse(hit("cisco"))
    }

    @Test
    fun searchMatchesCurrentAndHomeLocationOfCheckedOutItem() {
        assertTrue(InventoryQuery.matchesSearch(outToSam, "hangar"))
        assertTrue(InventoryQuery.matchesSearch(outToSam, "Room 214"))
    }

    @Test
    fun searchHandlesMissingSerial() {
        val row = testRow(testItem("T", serial = null))
        assertFalse(InventoryQuery.matchesSearch(row, "SN-"))
    }

    @Test
    fun searchAndFilterCombine() {
        assertEquals(listOf("A-2"), tags(InventoryFilter.CHECKED_OUT, search = "hangar"))
    }
}

