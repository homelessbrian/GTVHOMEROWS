package dev.tmdbrows.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ListConfig::class, CachedItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configs(): ListConfigDao
    abstract fun items(): CachedItemDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tmdbrows.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
