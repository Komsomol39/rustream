package com.komsomol.rustream.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rustream_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_DARK_THEME        = booleanPreferencesKey("dark_theme")
        val KEY_DOWNLOAD_PATH     = stringPreferencesKey("download_path")
        val KEY_RUTOR_ENABLED     = booleanPreferencesKey("rutor_enabled")
        val KEY_RUTRACKER_ENABLED = booleanPreferencesKey("rutracker_enabled")
        val KEY_KINOZAL_ENABLED   = booleanPreferencesKey("kinozal_enabled")
        val KEY_NNM_ENABLED       = booleanPreferencesKey("nnm_enabled")
        val KEY_YTS_ENABLED       = booleanPreferencesKey("yts_enabled")
        val KEY_RUTOR_DEBUG       = stringPreferencesKey("rutor_debug")
    }

    val darkTheme: Flow<Boolean>        = context.dataStore.data.map { it[KEY_DARK_THEME] ?: true }
    val downloadPath: Flow<String>      = context.dataStore.data.map { it[KEY_DOWNLOAD_PATH] ?: "/sdcard/RuStream" }
    val ruTorEnabled: Flow<Boolean>     = context.dataStore.data.map { it[KEY_RUTOR_ENABLED] ?: false }
    val ruTrackerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_RUTRACKER_ENABLED] ?: false }
    val kinozalEnabled: Flow<Boolean>   = context.dataStore.data.map { it[KEY_KINOZAL_ENABLED] ?: true }
    val nnmEnabled: Flow<Boolean>       = context.dataStore.data.map { it[KEY_NNM_ENABLED] ?: false }
    val ytsEnabled: Flow<Boolean>       = context.dataStore.data.map { it[KEY_YTS_ENABLED] ?: true }
    val rutorDebug: Flow<String>        = context.dataStore.data.map { it[KEY_RUTOR_DEBUG] ?: "" }

    suspend fun setDarkTheme(v: Boolean)        = context.dataStore.edit { it[KEY_DARK_THEME] = v }
    suspend fun setDownloadPath(v: String)       = context.dataStore.edit { it[KEY_DOWNLOAD_PATH] = v }
    suspend fun setRuTorEnabled(v: Boolean)      = context.dataStore.edit { it[KEY_RUTOR_ENABLED] = v }
    suspend fun setRuTrackerEnabled(v: Boolean)  = context.dataStore.edit { it[KEY_RUTRACKER_ENABLED] = v }
    suspend fun setKinozalEnabled(v: Boolean)    = context.dataStore.edit { it[KEY_KINOZAL_ENABLED] = v }
    suspend fun setNnmEnabled(v: Boolean)        = context.dataStore.edit { it[KEY_NNM_ENABLED] = v }
    suspend fun setYtsEnabled(v: Boolean)        = context.dataStore.edit { it[KEY_YTS_ENABLED] = v }
    suspend fun setRutorDebug(v: String)         = context.dataStore.edit { it[KEY_RUTOR_DEBUG] = v }
}
