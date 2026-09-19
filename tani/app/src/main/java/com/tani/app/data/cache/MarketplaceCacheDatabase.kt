package com.tani.app.data.cache

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(
    tableName = "marketplace_cache",
    indices = [Index(value = ["saved_at_epoch_ms"])]
)
data class MarketplaceCacheEntry(
    @PrimaryKey
    @androidx.room.ColumnInfo(name = "cache_key")
    val key: String,
    val version: Int,
    @androidx.room.ColumnInfo(name = "saved_at_epoch_ms")
    val savedAtEpochMs: Long,
    val payload: String
)

@Dao
interface MarketplaceCacheDao {
    @Query("SELECT * FROM marketplace_cache WHERE cache_key = :key LIMIT 1")
    suspend fun get(key: String): MarketplaceCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MarketplaceCacheEntry)

    @Query("DELETE FROM marketplace_cache WHERE cache_key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM marketplace_cache WHERE cache_key LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM marketplace_cache WHERE version != :currentVersion")
    suspend fun deleteOtherVersions(currentVersion: Int)
}

@Database(
    entities = [MarketplaceCacheEntry::class],
    version = 1,
    exportSchema = false
)
abstract class MarketplaceCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): MarketplaceCacheDao

    companion object {
        @Volatile
        private var instance: MarketplaceCacheDatabase? = null

        fun get(context: Context): MarketplaceCacheDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarketplaceCacheDatabase::class.java,
                    "tani_market_cache.db"
                )
                    // Cache data is disposable and can always be rebuilt from Supabase.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
