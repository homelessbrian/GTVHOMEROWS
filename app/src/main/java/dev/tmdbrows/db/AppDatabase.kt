package dev.tmdbrows.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ListConfig::class, CachedItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configs(): ListConfigDao
    abstract fun items(): CachedItemDao

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

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tmdbrows.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
