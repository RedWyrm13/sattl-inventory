package org.sattl.inventory.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream

/**
 * Helpers for files chosen in Android's file picker (Storage Access Framework, spec 11).
 * No storage permissions are needed: the picker grants access to just the chosen file.
 */
object Documents {
    /** The file name the user chose, e.g. "sattl-inventory-2026-10-01-1430.zip", if known. */
    fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Opens [uri] for writing, replacing any existing contents. */
    fun openForWriting(context: Context, uri: Uri): OutputStream {
        val resolver = context.contentResolver
        // "wt" truncates an existing file; a few providers only accept "w".
        return runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: error("The chosen location cannot be written to.")
    }

    fun openForReading(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("The chosen file cannot be opened.")
}
