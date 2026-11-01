package app.muro.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

enum class ListId { ADS, TRACKERS, CUSTOM }

@Entity(tableName = "log", indices = [Index("timestamp")])
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val timestamp: Long,
    val domain: String,
    val packageName: String?,
    val appName: String?,
    val blocked: Boolean,
)

@Entity(tableName = "blocked_domain", primaryKeys = ["list", "domain"])
data class BlockedDomain(val list: String, val domain: String)

@Entity(tableName = "allowed_domain")
data class AllowedDomain(@PrimaryKey val domain: String)

data class AppBlockCount(val packageName: String, val blocked: Int)

@Dao
interface LogDao {
    @Insert
    suspend fun insertAll(entries: List<LogEntry>)

    /** [blocked] en null trae todo; es el filtro "Todos" del registro. */
    @Query("SELECT * FROM log WHERE :blocked IS NULL OR blocked = :blocked ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun recent(blocked: Boolean?, limit: Int): Flow<List<LogEntry>>

    @Query("SELECT COUNT(*) FROM log WHERE blocked = 1 AND timestamp >= :since")
    fun blockedSince(since: Long): Flow<Int>

    @Query(
        "SELECT packageName, COUNT(*) AS blocked FROM log " +
            "WHERE blocked = 1 AND timestamp >= :since AND packageName IS NOT NULL GROUP BY packageName",
    )
    fun blockedByAppSince(since: Long): Flow<List<AppBlockCount>>

    @Query("DELETE FROM log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface DomainDao {
    @Query("SELECT domain FROM blocked_domain WHERE list IN (:lists)")
    fun observeBlocked(lists: List<String>): Flow<List<String>>

    @Query("SELECT domain FROM blocked_domain WHERE list = :list ORDER BY domain")
    fun observeList(list: String): Flow<List<String>>

    @Query("SELECT domain FROM allowed_domain")
    fun observeAllowed(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM blocked_domain WHERE list = :list")
    suspend fun count(list: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<BlockedDomain>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun allow(row: AllowedDomain)

    @Query("DELETE FROM allowed_domain WHERE domain = :domain")
    suspend fun removeAllowed(domain: String)

    @Query("DELETE FROM blocked_domain WHERE list = :list AND domain = :domain")
    suspend fun removeBlocked(list: String, domain: String)

    @Query("DELETE FROM blocked_domain WHERE list = :list")
    suspend fun clear(list: String)

    @Transaction
    suspend fun replaceList(list: String, domains: Collection<String>) {
        clear(list)
        insertAll(domains.map { BlockedDomain(list, it) })
    }
}

@Database(
    entities = [LogEntry::class, BlockedDomain::class, AllowedDomain::class],
    version = 1,
    exportSchema = false,
)
abstract class MuroDb : RoomDatabase() {
    abstract fun log(): LogDao
    abstract fun domains(): DomainDao
}
