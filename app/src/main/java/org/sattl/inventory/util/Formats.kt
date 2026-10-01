package org.sattl.inventory.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Display formats. Stored values are UTC; they are shown in the tablet's local time (spec 4). */
object Formats {
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun date(d: LocalDate): String = date.format(d)

    fun dateTime(epochMillis: Long): String =
        dateTime.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** Material date pickers use midnight UTC millis for a calendar date. */
    fun toPickerMillis(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}

/** Today's date in the tablet's time zone. */
fun Clock.today(): LocalDate = Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate()
