package com.otoradio.app.data

import com.otoradio.app.data.local.CatalogLoader
import com.otoradio.app.data.local.FavoriteEntity
import com.otoradio.app.data.local.HistoryEntity
import com.otoradio.app.data.local.PlaylistEntity
import com.otoradio.app.data.local.PlaylistTrackEntity
import com.otoradio.app.data.local.RadioDao
import com.otoradio.app.data.model.Station
import com.otoradio.app.data.remote.RadioBrowserApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val catalog: CatalogLoader,
    private val dao: RadioDao,
    private val radioBrowser: RadioBrowserApi
) {
    val localStations: List<Station> get() = catalog.stations
    val presets get() = catalog.presets

    private var remoteCache: List<Station> = emptyList()

    fun favorites(): Flow<List<Station>> =
        dao.favorites().map { list -> list.map { it.toStation() } }

    fun history(): Flow<List<Station>> = dao.history().map { list -> list.map { it.toStation() } }

    fun favoriteIds(): Flow<Set<String>> = dao.favorites().map { it.map { f -> f.stationId }.toSet() }

    suspend fun toggleFavorite(station: Station) {
        val ids = dao.favoriteIds()
        if (ids.contains(station.id)) dao.removeFavorite(station.id)
        else dao.addFavorite(FavoriteEntity.from(station))
    }

    suspend fun isFavorite(id: String): Boolean = dao.favoriteIds().contains(id)

    suspend fun recordHistory(station: Station) {
        dao.addHistory(HistoryEntity.from(station))
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun refreshRemote(): Int {
        val remote = radioBrowser.topIndonesia()
        remoteCache = remote
        return remote.size
    }

    suspend fun searchRemote(query: String): List<Station> = radioBrowser.search(query)

    /** Katalog lokal (741 stasiun) + hasil Radio Browser bila sudah dimuat. */
    fun allStations(): List<Station> = (localStations + remoteCache).distinctBy { it.name.lowercase() + it.streamUrl }

    fun search(query: String): List<Station> {
        if (query.isBlank()) return allStations()
        val q = query.trim().lowercase()
        return allStations().filter { s ->
            s.name.lowercase().contains(q) ||
                (s.city?.lowercase()?.contains(q) == true) ||
                (s.frequency?.lowercase()?.contains(q) == true) ||
                (s.genre?.lowercase()?.contains(q) == true)
        }
    }

    fun cities(): List<String> = localStations.mapNotNull { it.city }.distinct().sorted()

    fun stationById(id: String): Station? =
        localStations.firstOrNull { it.id == id } ?: remoteCache.firstOrNull { it.id == id }

    // ---------- playlist ----------
    data class PlaylistSummary(val id: String, val name: String, val count: Int)

    fun playlistSummaries(): Flow<List<PlaylistSummary>> =
        combine(dao.playlists(), dao.playlistCounts()) { lists, counts ->
            val map = counts.associate { it.playlistId to it.total }
            lists.map { PlaylistSummary(it.id, it.name, map[it.id] ?: 0) }
        }

    fun playlistStations(id: String): Flow<List<Station>> =
        dao.playlistTracks(id).map { list -> list.map { it.toStation() } }

    suspend fun playlistStationsOnce(id: String): List<Station> =
        dao.playlistTracksOnce(id).map { it.toStation() }

    suspend fun createPlaylist(name: String): String {
        val id = "pl-" + java.util.UUID.randomUUID().toString().take(8)
        dao.insertPlaylist(PlaylistEntity(id = id, name = name.trim(), createdAt = System.currentTimeMillis()))
        return id
    }

    suspend fun renamePlaylist(id: String, name: String) = dao.renamePlaylist(id, name.trim())

    suspend fun deletePlaylist(id: String) {
        dao.deletePlaylistTracks(id)
        dao.deletePlaylist(id)
    }

    /** @return false bila stasiun sudah ada di playlist. */
    suspend fun addToPlaylist(playlistId: String, station: Station): Boolean {
        if (dao.trackExists(playlistId, station.id) > 0) return false
        val pos = dao.nextPosition(playlistId)
        dao.insertTrack(PlaylistTrackEntity.from(playlistId, station, pos))
        return true
    }

    suspend fun removeFromPlaylist(playlistId: String, stationId: String) =
        dao.removeTrack(playlistId, stationId)

    suspend fun eqProfile(id: String) = dao.eqProfile(id)
    suspend fun saveEqProfile(p: com.otoradio.app.data.local.EqProfileEntity) = dao.saveEqProfile(p)
    suspend fun deleteEqProfile(id: String) = dao.deleteEqProfile(id)
}
