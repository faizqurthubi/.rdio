package com.otoradio.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("oto_radio_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class EqScope { GLOBAL, PER_STATION }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val useIcyTitle: Boolean = true,
    val autoReconnect: Boolean = true,
    val autoPlayLastStation: Boolean = false,
    val sleepTimerDefaultMin: Int = 30,
    val eqScope: EqScope = EqScope.GLOBAL,
    val lastStationId: String? = null
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYN = booleanPreferencesKey("dynamic_color")
        val ICY = booleanPreferencesKey("use_icy")
        val RECONNECT = booleanPreferencesKey("auto_reconnect")
        val AUTOPLAY = booleanPreferencesKey("auto_play_last")
        val SLEEP = intPreferencesKey("sleep_default_min")
        val EQ_SCOPE = stringPreferencesKey("eq_scope")
        val LAST = stringPreferencesKey("last_station_id")
        val EQ_CONFIG = stringPreferencesKey("eq_config")
        val EQ_CUSTOM_PRESETS = stringPreferencesKey("eq_custom_presets")
        val EQ_RAW_DEFAULT = booleanPreferencesKey("eq_raw_default_v2")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: ThemeMode.DARK.name) }.getOrDefault(ThemeMode.DARK),
            dynamicColor = p[Keys.DYN] ?: true,
            useIcyTitle = p[Keys.ICY] ?: true,
            autoReconnect = p[Keys.RECONNECT] ?: true,
            autoPlayLastStation = p[Keys.AUTOPLAY] ?: false,
            sleepTimerDefaultMin = p[Keys.SLEEP] ?: 30,
            eqScope = runCatching { EqScope.valueOf(p[Keys.EQ_SCOPE] ?: EqScope.GLOBAL.name) }.getOrDefault(EqScope.GLOBAL),
            lastStationId = p[Keys.LAST]
        )
    }

    val eqConfigJson: Flow<String?> = context.dataStore.data.map { it[Keys.EQ_CONFIG] }
    val customPresetsJson: Flow<String?> = context.dataStore.data.map { it[Keys.EQ_CUSTOM_PRESETS] }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.DYN] = on }
    suspend fun setUseIcy(on: Boolean) = context.dataStore.edit { it[Keys.ICY] = on }
    suspend fun setAutoReconnect(on: Boolean) = context.dataStore.edit { it[Keys.RECONNECT] = on }
    suspend fun setAutoPlayLast(on: Boolean) = context.dataStore.edit { it[Keys.AUTOPLAY] = on }
    suspend fun setSleepDefault(min: Int) = context.dataStore.edit { it[Keys.SLEEP] = min }
    suspend fun setEqScope(scope: EqScope) = context.dataStore.edit { it[Keys.EQ_SCOPE] = scope.name }
    suspend fun setEqConfig(json: String) = context.dataStore.edit { it[Keys.EQ_CONFIG] = json }

    /** true bila migrasi "default EQ mati" sudah dijalankan. */
    suspend fun eqRawDefaultApplied(): Boolean =
        context.dataStore.data.first()[Keys.EQ_RAW_DEFAULT] ?: false

    suspend fun setEqRawDefaultApplied() =
        context.dataStore.edit { it[Keys.EQ_RAW_DEFAULT] = true }
    suspend fun setCustomPresets(json: String) = context.dataStore.edit { it[Keys.EQ_CUSTOM_PRESETS] = json }
    suspend fun setLastStation(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.LAST) else it[Keys.LAST] = id
    }
}
