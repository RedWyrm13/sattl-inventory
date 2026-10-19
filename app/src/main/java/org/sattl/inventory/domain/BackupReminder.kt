package org.sattl.inventory.domain

/**
 * Spec 7.1: if the last full export is more than 30 days old, or there has never been one,
 * the admin sees a reminder banner on the Inventory screen.
 */
object BackupReminder {
    const val MAX_AGE_DAYS = 30
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** Banner text, or null when no reminder is due. */
    fun message(lastExportAt: Long?, now: Long): String? {
        if (lastExportAt == null) return "No backup has been made yet."
        val days = ((now - lastExportAt) / DAY_MILLIS).coerceAtLeast(0)
        return if (days > MAX_AGE_DAYS) "Last backup: $days days ago." else null
    }
}
