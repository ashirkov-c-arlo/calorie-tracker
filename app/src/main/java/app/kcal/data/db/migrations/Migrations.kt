package app.kcal.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the one-line meal summary. Meals stored before it keep `NULL`, which the journal renders
 * by listing their item names instead.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meal_entries ADD COLUMN summary TEXT DEFAULT NULL")
    }
}
