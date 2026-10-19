package org.sattl.inventory.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hand-written RFC 4180 CSV writer and reader (spec 7.1, section 11). */
class CsvTest {

    @Test
    fun startsWithBomAndUsesCrlf() {
        val text = Csv.write(listOf(listOf("a", "b"), listOf("1", "2")))
        assertTrue(text.startsWith("﻿"))
        assertEquals("﻿a,b\r\n1,2\r\n", text)
    }

    @Test
    fun quotesOnlyWhenNeeded() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", Csv.escape("line1\nline2"))
        assertEquals("\" padded \"", Csv.escape(" padded "))
        assertEquals("", Csv.escape(null))
    }

    @Test
    fun roundTripsAwkwardValues() {
        val rows = listOf(
            listOf("tag", "notes", "empty"),
            listOf("SATTL-001", "Comma, \"quotes\" and\r\nnew lines", ""),
            listOf("Ünïcødé ✓ 日本", "  spaces  ", "x"),
            listOf("", "", ""),
        )
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }

    @Test
    fun readsLfOnlyFilesAndFilesWithoutBomOrFinalNewline() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a,b\n1,2"))
    }

    @Test
    fun skipsBlankLines() {
        assertEquals(listOf(listOf("a"), listOf("b")), Csv.parse("a\r\n\r\nb\r\n"))
    }

    @Test
    fun reportsUnclosedQuoteWithLineNumber() {
        val e = runCatching { Csv.parse("a,b\r\n1,\"oops\r\n") }.exceptionOrNull() as CsvException
        assertEquals(2, e.line)
    }

    @Test
    fun reportsTextAfterClosingQuote() {
        assertTrue(runCatching { Csv.parse("\"a\"b,c") }.exceptionOrNull() is CsvException)
    }
}
