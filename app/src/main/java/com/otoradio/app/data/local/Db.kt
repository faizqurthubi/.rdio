package com.otoradio.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.otoradio.app.data.model.Station
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val streamUrl: String,
    val altUrls: String,
    val frequency: String?,
    val city: String?,
    val website: String?,
    val logoFile: String?,
    val logoAsset: String?,
    val addedAt: Long
) {
    fun toStation() = Station(
        id = stationId,
        name = name,
        streamUrl = streamUrl,
        altUrls = altUrls.split('\n').filter { it.isNotBlank() },
        frequency = frequency,
        city = city,
        website = website,
        logoFile = logoFile,
        logoAsset = logoAsset,
        source = "favorite"
    )

    companion object {
        fun from(s: Station, now: Long = System.currentTimeMillis()) = FavoriteEntity(
            stationId = s.id,
            name = s.name,
            streamUrl = s.streamUrl,
            altUrls = s.altUrls.joinToString("\n"),
            frequency = s.frequency,
            city = s.city,
            website = s.website,
            logoFile = s.logoFile,
            logoAsset = s.logoAsset,
            addedAt = now
        )
    }
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val streamUrl: String,
    val altUrls: String,
    val frequency: String?,
    val city: String?,
    val website: String?,
    val logoFile: String?,
    val logoAsset: String?,
    val playedAt: Long
) {
    fun toStation() = Station(
        id = stationId,
        name = name,
        streamUrl = streamUrl,
        altUrls = altUrls.split('\n').filter { it.isNotBlank() },
        frequency = frequency,
        city = city,
        website = website,
        logoFile = logoFile,
        logoAsset = logoAsset,
        source = "history"
    )

    companion object {
        fun from(s: Station, now: Long = System.currentTimeMillis()) = HistoryEntity(
            stationId = s.id,
            name = s.name,
            streamUrl = s.streamUrl,
            altUrls = s.altUrls.joinToString("\n"),
            frequency = s.frequency,
            city = s.city,
            website = s.website,
            logoFile = s.logoFile,
            logoAsset = s.logoAsset,
            playedAt = now
        )
    }
}

@Entity(tableName = "eq_profiles")
data class EqProfileEntity(
    @PrimaryKey val stationId: String,
    val presetId: String,
    val gains: String,          // comma separated dB
    val bassBoost: Int,         // 0..1000 mB
    val virtualizer: Int,       // 0..1000
    val loudness: Int           // 0..2000 mB
)


@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

/** Satu stasiun di dalam playlist; menyimpan snapshot supaya tetap jalan tanpa katalog. */
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "stationId"])
data class PlaylistTrackEntity(
    val playlistId: String,
    val stationId: String,
    val name: String,
    val streamUrl: String,
    val altUrls: String,
    val frequency: String?,
    val city: String?,
    val website: String?,
    val logoFile: String?,
    val logoAsset: String?,
    val position: Int,
    val addedAt: Long
) {
    fun toStation() = Station(
        id = stationId,
        name = name,
        streamUrl = streamUrl,
        altUrls = altUrls.split('\n').filter { it.isNotBlank() },
        frequency = frequency,
        city = city,
        website = website,
        logoFile = logoFile,
        logoAsset = logoAsset,
        source = "playlist"
    )

    companion object {
        fun from(playlistId: String, s: Station, position: Int, now: Long = System.currentTimeMillis()) =
            PlaylistTrackEntity(
                playlistId = playlistId,
                stationId = s.id,
                name = s.name,
                streamUrl = s.streamUrl,
                altUrls = s.altUrls.joinToString("\n"),
                frequency = s.frequency,
                city = s.city,
                website = s.website,
                logoFile = s.logoFile,
                logoAsset = s.logoAsset,
                position = position,
                addedAt = now
            )
    }
}

/** Hasil agregasi jumlah trek per playlist. */
data class PlaylistCount(val playlistId: String, val total: Int)

@Dao
interface RadioDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun favorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 100")
    fun history(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE stationId = :id")
    suspend fun removeFavorite(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE stationId = :id)")
    fun isFavorite(id: String): Flow<Boolean>

    @Query("SELECT stationId FROM favorites")
    suspend fun favoriteIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(item: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM eq_profiles WHERE stationId = :id")
    suspend fun eqProfile(id: String): EqProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEqProfile(p: EqProfileEntity)

    @Query("DELETE FROM eq_profiles WHERE stationId = :id")
    suspend fun deleteEqProfile(id: String)

    // ---------- playlist ----------
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT playlistId, COUNT(*) AS total FROM playlist_tracks GROUP BY playlistId")
    fun playlistCounts(): Flow<List<PlaylistCount>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position ASC")
    fun playlistTracks(id: String): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position ASC")
    suspend fun playlistTracksOnce(id: String): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(p: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun deletePlaylistTracks(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(t: PlaylistTrackEntity)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_tracks WHERE playlistId = :id")
    suspend fun nextPosition(id: String): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id AND stationId = :stationId")
    suspend fun removeTrack(id: String, stationId: String)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :id AND stationId = :stationId")
    suspend fun trackExists(id: String, stationId: String): Int
}

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        EqProfileEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun dao(): RadioDao

    companion object {
        /** v1 -> v2: tambah tabel playlist tanpa menghapus favorit/riwayat pengguna. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_tracks` (" +
                        "`playlistId` TEXT NOT NULL, `stationId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`streamUrl` TEXT NOT NULL, `altUrls` TEXT NOT NULL, `frequency` TEXT, " +
                        "`city` TEXT, `website` TEXT, `logoFile` TEXT, `logoAsset` TEXT, " +
                        "`position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `stationId`))"
                )
            }
        }
    }
}
