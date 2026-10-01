package org.sattl.inventory.data.db

import androidx.room.migration.Migration

/**
 * Explicit schema migrations (spec section 11). The app must NEVER use destructive
 * migration, because that would erase the lab's inventory on update.
 *
 * To change the schema:
 *  1. Bump [AppDatabase.VERSION].
 *  2. Add a Migration(old, new) object below that alters the tables with SQL.
 *  3. Add it to [ALL].
 *  4. Build once so Room writes app/schemas/<new version>.json, and commit that file.
 *  5. If the checkouts table is rebuilt, call DbConstraints.create(db) inside the migration.
 */
object Migrations {
    /** Version 1 is the first release, so there is nothing to migrate yet. */
    val ALL: Array<Migration> = arrayOf()
}
