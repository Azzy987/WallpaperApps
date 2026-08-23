package com.droidates.wallpapers.core.data.local

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Preserves saved favourites when an existing app moves onto the shared `:core` codebase.
 *
 * Each standalone app used its own table name — `oneplus7favorites`, `s26ultrafavorites`,
 * `xiaomi17ultrafavorites`, `ios27favorites`, `s25favorites`, `iphone17favorites` — while
 * `:core` uses a single `favorites` table. The database *file* is the same
 * (`wallpaper_database`) for every app, so an updating user arrives with the old table
 * present and the new one missing.
 *
 * Without this, Room hits a schema mismatch and destroys the database: every user loses
 * their saved wallpapers on update.
 *
 * ## Why this covers three source versions
 *
 * The live apps did NOT all ship the same `@Database(version = …)`:
 *
 *  - `s25favorites`           → version 1
 *  - most others              → version 2
 *  - `ios27favorites`         → version 3
 *
 * `:core` is therefore version 4, with a migration registered from every one of those, so
 * no installed app can arrive at a version Room has no path for. In particular iOS 27 was
 * *already* at 3: had `:core` also been 3, Room would have run no migration at all and
 * every favourites query would have failed at runtime against a table that isn't there.
 *
 * The column layout is byte-for-byte identical between every old table and the new one
 * (verified against all seven `FavoriteEntity` definitions), so a plain
 * `ALTER TABLE … RENAME TO` is enough — no data copy, no risk of partial migration.
 */

/** Latest schema version. Bump this *and* add a migration below when the schema changes. */
const val DB_VERSION = 4

/**
 * Migrations from every version a live app may be sitting on, up to [DB_VERSION].
 *
 * All of them do the same thing — the destination schema is identical regardless of where
 * the user started, because the only pre-existing table was the favourites one.
 *
 * @param legacyTableName the app's previous table, from its AppSpec.
 */
fun legacyFavoritesMigrations(legacyTableName: String): Array<Migration> =
    arrayOf(
        renameFavorites(1, DB_VERSION, legacyTableName),
        renameFavorites(2, DB_VERSION, legacyTableName),
        renameFavorites(3, DB_VERSION, legacyTableName),
    )

private fun renameFavorites(from: Int, to: Int, legacyTableName: String): Migration =
    object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                val hasCurrent = db.hasTable("favorites")

                // Brand-new apps (pixel11, iphone18) already use "favorites" and have no
                // legacy table — but a user upgrading from an older :core build still needs
                // the table to exist, so fall through to the create below.
                val hasLegacy = legacyTableName != "favorites" && db.hasTable(legacyTableName)

                when {
                    hasLegacy && !hasCurrent -> {
                        db.execSQL("ALTER TABLE `$legacyTableName` RENAME TO `favorites`")
                        Log.i(TAG, "v$from: renamed $legacyTableName -> favorites")
                    }
                    hasLegacy -> {
                        // Both exist (rare: a reinstall created `favorites` first). Merge the
                        // legacy rows in rather than dropping them, then discard the old table.
                        db.execSQL(
                            "INSERT OR IGNORE INTO `favorites` SELECT * FROM `$legacyTableName`"
                        )
                        db.execSQL("DROP TABLE `$legacyTableName`")
                        Log.i(TAG, "v$from: merged $legacyTableName into favorites")
                    }
                    !hasCurrent -> {
                        db.execSQL(CREATE_FAVORITES)
                        Log.i(TAG, "v$from: created favorites table")
                    }
                    else -> Log.i(TAG, "v$from: favorites already current, nothing to do")
                }
            } catch (e: SQLiteException) {
                // Never let a migration failure crash startup. Make sure the table the DAO
                // queries at least exists, or every favourites read will throw.
                Log.e(TAG, "Favourites migration failed for $legacyTableName", e)
                try {
                    db.execSQL(CREATE_FAVORITES)
                } catch (inner: SQLiteException) {
                    Log.e(TAG, "Could not create fallback favorites table", inner)
                }
            }
        }
    }

private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
    query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
        .use { it.count > 0 }

private const val TAG = "FavoritesMigration"

private const val CREATE_FAVORITES = """
    CREATE TABLE IF NOT EXISTS `favorites` (
        `wallpaperId` TEXT NOT NULL,
        `wallpaperName` TEXT NOT NULL,
        `imageUrl` TEXT NOT NULL,
        `thumbnail` TEXT NOT NULL,
        `category` TEXT NOT NULL,
        `downloads` INTEGER NOT NULL,
        `views` INTEGER NOT NULL,
        `dimensions` TEXT NOT NULL,
        `size` TEXT NOT NULL,
        `exclusive` INTEGER NOT NULL,
        `timestamp` INTEGER NOT NULL,
        PRIMARY KEY(`wallpaperId`)
    )
"""
