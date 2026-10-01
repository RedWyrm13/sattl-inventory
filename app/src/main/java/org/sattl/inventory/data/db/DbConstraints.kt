package org.sattl.inventory.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database rules that Room annotations cannot express.
 *
 * Rule 6.1 / spec section 4: "A database constraint must guarantee at most one open checkout
 * per item." SQLite could do this with a partial unique index, but Room's schema validation
 * (run after every migration) would reject an index it does not know about. Triggers are
 * ignored by that validation, so we use triggers instead.
 *
 * [create] runs when the database is first created. Any future migration that rebuilds the
 * checkouts table must call [create] again, because dropping a table drops its triggers.
 */
object DbConstraints {

    const val ONE_OPEN_CHECKOUT_ERROR = "Item already has an open checkout"

    fun create(db: SupportSQLiteDatabase) {
        // Block inserting a second open checkout for the same item.
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS checkouts_one_open_per_item_insert
            BEFORE INSERT ON checkouts
            WHEN NEW.checkedInAt IS NULL AND EXISTS (
                SELECT 1 FROM checkouts WHERE itemId = NEW.itemId AND checkedInAt IS NULL
            )
            BEGIN
                SELECT RAISE(ABORT, '$ONE_OPEN_CHECKOUT_ERROR');
            END
            """.trimIndent()
        )
        // Block re-opening (or moving) a checkout onto an item that already has an open one.
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS checkouts_one_open_per_item_update
            BEFORE UPDATE ON checkouts
            WHEN NEW.checkedInAt IS NULL AND EXISTS (
                SELECT 1 FROM checkouts
                WHERE itemId = NEW.itemId AND checkedInAt IS NULL AND id != NEW.id
            )
            BEGIN
                SELECT RAISE(ABORT, '$ONE_OPEN_CHECKOUT_ERROR');
            END
            """.trimIndent()
        )
    }
}
