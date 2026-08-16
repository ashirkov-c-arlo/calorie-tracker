package app.kcal.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.data.db.migrations.MIGRATION_1_2
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A meal stored before summaries existed must survive the upgrade with `summary = NULL`, because
 * the journal renders those rows from their item names instead.
 *
 * `androidx.room:room-testing` is not a dependency, so the v1 database is written by hand from
 * the committed `schemas/app.kcal.data.db.KcalDatabase/1.json` and then opened through Room,
 * which validates the migrated schema against the v2 entities.
 */
@RunWith(AndroidJUnit4::class)
class KcalDatabaseMigrationTest {

    @Test
    fun `migrating from 1 to 2 keeps meals and leaves their summary empty`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-1-to-2.db")
        file.parentFile?.mkdirs()
        file.delete()
        createVersion1Database(file.path)

        val database = Room.databaseBuilder(context, KcalDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val meal = database.mealEntryDao().findById(1)
            assertEquals("oatmeal", meal?.meal?.rawUserInput)
            assertNull(meal?.meal?.summary)
            assertEquals(listOf("Oatmeal"), meal?.items?.map { it.name })
        } finally {
            database.close()
            file.delete()
        }
    }

    private fun createVersion1Database(path: String) {
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            VERSION_1_SCHEMA.forEach(db::execSQL)
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$VERSION_1_HASH')")
            db.execSQL(
                "INSERT INTO meal_entries (id, local_date_epoch_day, at_epoch_millis, raw_user_input, source) " +
                    "VALUES (1, 20000, 1728000000000, 'oatmeal', 'MANUAL')",
            )
            db.execSQL(
                "INSERT INTO food_items " +
                    "(meal_entry_id, position, name, grams, kcal, protein_g, fat_g, carbs_g, " +
                    "confidence, needs_review) " +
                    "VALUES (1, 0, 'Oatmeal', 60.0, 228, 8.1, 4.1, 36.0, 0.9, 0)",
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val VERSION_1_HASH = "a9a3cd8449088e0544e23a6f0c3843b6"

        val VERSION_1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `meal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`local_date_epoch_day` INTEGER NOT NULL, `at_epoch_millis` INTEGER NOT NULL, " +
                "`raw_user_input` TEXT, `source` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_meal_entries_local_date_epoch_day` " +
                "ON `meal_entries` (`local_date_epoch_day`)",
            "CREATE TABLE IF NOT EXISTS `food_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`meal_entry_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`grams` REAL, `kcal` INTEGER NOT NULL, `protein_g` REAL NOT NULL, `fat_g` REAL NOT NULL, " +
                "`carbs_g` REAL NOT NULL, `confidence` REAL NOT NULL, `needs_review` INTEGER NOT NULL, " +
                "FOREIGN KEY(`meal_entry_id`) REFERENCES `meal_entries`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_food_items_meal_entry_id_position` " +
                "ON `food_items` (`meal_entry_id`, `position`)",
            "CREATE TABLE IF NOT EXISTS `weight_entries` (`local_date_epoch_day` INTEGER NOT NULL, " +
                "`kg` REAL NOT NULL, PRIMARY KEY(`local_date_epoch_day`))",
            "CREATE TABLE IF NOT EXISTS `daily_target_snapshots` (`local_date_epoch_day` INTEGER NOT NULL, " +
                "`kcal` INTEGER NOT NULL, `protein_g` REAL NOT NULL, `fat_g` REAL NOT NULL, " +
                "`carbs_g` REAL NOT NULL, `effective_loss_rate_kg_week` REAL NOT NULL, " +
                "PRIMARY KEY(`local_date_epoch_day`))",
        )
    }
}
