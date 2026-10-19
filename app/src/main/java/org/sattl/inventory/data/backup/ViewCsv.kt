package org.sattl.inventory.data.backup

import org.sattl.inventory.data.model.InventoryRow
import org.sattl.inventory.domain.InventoryFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Export this view" (spec 7.2): one CSV of exactly what the Inventory list shows, in the
 * same order and with the same columns. With the Checked out or My checkouts filter (the
 * filters that show checkout details on screen) it adds borrower, checkout date, expected
 * return date, destination and reason.
 */
object ViewCsv {
    private val BASE = listOf("SATTL tag", "Manufacturer", "Model", "Serial", "Current location", "Status")
    private val CHECKOUT = listOf("Borrower", "Checkout date", "Expected return date", "Destination", "Reason")

    fun showsCheckoutColumns(filter: InventoryFilter) =
        filter == InventoryFilter.CHECKED_OUT || filter == InventoryFilter.MY_CHECKOUTS

    fun write(rows: List<InventoryRow>, filter: InventoryFilter): String {
        val withCheckout = showsCheckoutColumns(filter)
        val header = if (withCheckout) BASE + CHECKOUT else BASE
        val lines = rows.map { r ->
            val base = listOf(
                r.item.sattlTag, r.item.manufacturer, r.item.modelNumber, r.item.serialNumber,
                r.currentLocation, r.status.label,
            )
            if (!withCheckout) base
            else base + listOf(r.borrowerName, r.checkoutDate?.toString(), r.expectedReturnDate?.toString(), r.destination, r.reason)
        }
        return Csv.write(listOf(header) + lines)
    }

    /** e.g. sattl-inventory-view-checked-out-2026-10-01-1430.csv */
    fun fileName(filter: InventoryFilter, epochMillis: Long, zone: ZoneId): String {
        val slug = filter.label.lowercase().replace(' ', '-')
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        return "sattl-inventory-view-$slug-$time.csv"
    }
}
