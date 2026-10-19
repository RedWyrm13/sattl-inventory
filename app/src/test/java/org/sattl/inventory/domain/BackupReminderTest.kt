package org.sattl.inventory.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 7.1: reminder when the last backup is more than 30 days old, or never made. */
class BackupReminderTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun neverBackedUp() {
        assertEquals("No backup has been made yet.", BackupReminder.message(null, now))
    }

    @Test
    fun noReminderUpToThirtyDays() {
        assertNull(BackupReminder.message(now, now))
        assertNull(BackupReminder.message(now - 30 * day, now))
    }

    @Test
    fun reminderAfterThirtyDays() {
        assertEquals("Last backup: 31 days ago.", BackupReminder.message(now - 31 * day, now))
    }
}
