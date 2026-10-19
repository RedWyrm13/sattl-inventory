package org.sattl.inventory.data.backup

/** A CSV file that could not be read; [line] is the 1-based line number, as Excel shows it. */
class CsvException(val line: Int, message: String) : Exception(message)

/**
 * A small RFC 4180 CSV writer and reader (spec section 11), used for every export.
 *
 * Format (spec 7.1): UTF-8 with a byte-order mark so Excel detects the encoding, comma
 * separated, a header row, CRLF line endings, and a value quoted only when it contains a
 * comma, quote, line break, or leading/trailing space. Quotes inside a value are doubled.
 *
 * CSV cannot tell an empty value from a missing one, so null is written as an empty value
 * and read back as "". The app never stores empty strings for optional fields (it stores
 * null), so this round-trips exactly.
 */
object Csv {
    const val BOM = '﻿'

    /** Builds a whole CSV document: BOM, then one line per row. */
    fun write(rows: List<List<String?>>): String = buildString {
        append(BOM)
        rows.forEach { row ->
            row.joinTo(this, separator = ",") { escape(it) }
            append("\r\n")
        }
    }

    fun escape(value: String?): String {
        if (value == null) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(' ') || value.endsWith(' ')
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /**
     * Parses a CSV document into rows of fields. Accepts CRLF or LF line endings and an
     * optional BOM. Completely empty lines are skipped. Throws [CsvException] on a quote
     * that is never closed or text straight after a closing quote.
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var afterClosingQuote = false
        var line = 1
        var rowStartLine = 1
        var i = if (text.startsWith(BOM)) 1 else 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
            afterClosingQuote = false
        }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> { inQuotes = false; afterClosingQuote = true }
                    else -> { if (c == '\n') line++; field.append(c) }
                }
            } else {
                when (c) {
                    ',' -> endField()
                    '\r' -> Unit // the '\n' that follows ends the row
                    '\n' -> { endRow(); line++; rowStartLine = line }
                    '"' -> {
                        if (field.isNotEmpty() || afterClosingQuote) {
                            throw CsvException(line, "Unexpected quote character in the middle of a value.")
                        }
                        inQuotes = true
                    }
                    else -> {
                        if (afterClosingQuote) throw CsvException(line, "Unexpected text after a closing quote.")
                        field.append(c)
                    }
                }
            }
            i++
        }
        if (inQuotes) throw CsvException(rowStartLine, "A quoted value is never closed.")
        if (field.isNotEmpty() || row.isNotEmpty() || afterClosingQuote) endRow()
        return rows
    }
}
