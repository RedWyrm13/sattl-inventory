package org.sattl.inventory.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Calendar dates (checkout date, date into inventory, ...) are stored as ISO-8601 text,
 * e.g. "2026-10-01". This sorts correctly and reads clearly in the database and in CSV.
 * Timestamps (createdAt, checkedInAt, ...) are plain Long UTC epoch milliseconds instead.
 */
class Converters {
    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
