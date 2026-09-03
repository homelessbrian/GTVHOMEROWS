package dev.tmdbrows.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ListConfig::class, CachedItem::class, CustomTarget::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configs(): ListConfigDao
    abstract fun items(): CachedItemDao
    abstract fun customTargets(): CustomTargetDao

    companion object {
        /** Adds the Discover/preset columns; existing list rows keep working untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE list_config ADD COLUMN kind TEXT NOT NULL DEFAULT 'LIST'")
                db.execSQL("ALTER TABLE list_config ADD COLUMN discoverJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE list_config ADD COLUMN presetId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE list_config ADD COLUMN presetMediaKind TEXT NOT NULL DEFAULT 'movie'")
                db.execSQL("ALTER TABLE list_config ADD COLUMN presetMaxItems INTEGER NOT NULL DEFAULT 40")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE list_config ADD COLUMN catalogId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS custom_target (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "packageName TEXT NOT NULL, label TEXT NOT NULL, template TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE list_config ADD COLUMN artStyle TEXT NOT NULL DEFAULT 'poster'")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tmdbrows.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
