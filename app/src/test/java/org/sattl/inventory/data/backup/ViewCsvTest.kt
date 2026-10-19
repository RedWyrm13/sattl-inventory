package org.sattl.inventory.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sattl.inventory.domain.InventoryFilter
import org.sattl.inventory.domain.sam
import org.sattl.inventory.domain.testItem
import org.sattl.inventory.domain.testRow
import java.time.ZoneOffset

/** Spec 7.2 "Export this view". */
class ViewCsvTest {
    private val rows = listOf(
        testRow(testItem("A-1", serial = null)),
        testRow(testItem("A-2"), borrower = sam, destination = "Hangar, bay 3"),
    )

    @Test
    fun basicViewHasListColumnsOnly() {
        val parsed = Csv.parse(ViewCsv.write(rows, InventoryFilter.ALL))
        assertEquals(listOf("SATTL tag", "Manufacturer", "Model", "Serial", "Current location", "Status"), parsed[0])
        assertEquals(listOf("A-1", "Ettus", "B210", "", "Room 214", "Available"), parsed[1])
        assertEquals(listOf("A-2", "Ettus", "B210", "SN-A-2", "Hangar, bay 3", "Checked out"), parsed[2])
    }

    @Test
    fun checkedOutViewAddsCheckoutColumns() {
        val parsed = Csv.parse(ViewCsv.write(rows.drop(1), InventoryFilter.CHECKED_OUT))
        assertEquals(
            listOf("Borrower", "Checkout date", "Expected return date", "Destination", "Reason"),
            parsed[0].drop(6),
        )
        assertEquals(listOf("Sam", "2026-10-01", "", "Hangar, bay 3", "Demo"), parsed[1].drop(6))
    }

    @Test
    fun myCheckoutsAlsoHasCheckoutColumns() {
        assertEquals(11, Csv.parse(ViewCsv.write(emptyList(), InventoryFilter.MY_CHECKOUTS))[0].size)
    }

    @Test
    fun fileNameNamesTheFilter() {
        val t = java.time.LocalDateTime.of(2026, 10, 1, 14, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(
            "sattl-inventory-view-checked-out-2026-10-01-1430.csv",
            ViewCsv.fileName(InventoryFilter.CHECKED_OUT, t, ZoneOffset.UTC),
        )
    }
}
