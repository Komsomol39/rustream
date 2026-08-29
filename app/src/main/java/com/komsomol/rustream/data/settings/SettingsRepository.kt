package com.komsomol.rustream.data.settings

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
        val KEY_AUTO_UPDATE       = booleanPreferencesKey("auto_update_check")
        val KEY_DOWNLOAD_PATH     = stringPreferencesKey("download_path")
        val KEY_RUTOR_ENABLED     = booleanPreferencesKey("rutor_enabled")
        val KEY_RUTRACKER_ENABLED = booleanPreferencesKey("rutracker_enabled")
        val KEY_KINOZAL_ENABLED   = booleanPreferencesKey("kinozal_enabled")
        val KEY_NNM_ENABLED       = booleanPreferencesKey("nnm_enabled")
        val KEY_TPB_ENABLED     = booleanPreferencesKey("tpb_enabled")
        val KEY_NEWPIPE_ENABLED   = booleanPreferencesKey("newpipe_enabled")
        val KEY_MEDIA_FOLDERS     = stringPreferencesKey("media_folders")
        // Лимиты торрент-сессии. 0 = без ограничения
        val KEY_DL_LIMIT_KB       = intPreferencesKey("dl_limit_kb")
        val KEY_UL_LIMIT_KB       = intPreferencesKey("ul_limit_kb")
        val KEY_MAX_ACTIVE        = intPreferencesKey("max_active_downloads")
        // Галка "регулировать": без неё скорость не ограничивается вовсе.
        // Значение в КБ/с при этом хранится отдельно, ноль = выключить совсем.
        val KEY_DL_LIMIT_ON       = booleanPreferencesKey("dl_limit_enabled")
        val KEY_UL_LIMIT_ON       = booleanPreferencesKey("ul_limit_enabled")
    }

    val darkTheme: Flow<Boolean>        = context.dataStore.data.map { it[KEY_DARK_THEME] ?: true }
    val autoUpdateCheck: Flow<Boolean>  = context.dataStore.data.map { it[KEY_AUTO_UPDATE] ?: true }
    val downloadPath: Flow<String>      = context.dataStore.data.map { it[KEY_DOWNLOAD_PATH] ?: "/sdcard/RuStream" }
    val ruTorEnabled: Flow<Boolean>     = context.dataStore.data.map { it[KEY_RUTOR_ENABLED] ?: true }
    val ruTrackerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_RUTRACKER_ENABLED] ?: false }
    val kinozalEnabled: Flow<Boolean>   = context.dataStore.data.map { it[KEY_KINOZAL_ENABLED] ?: true }
    val nnmEnabled: Flow<Boolean>       = context.dataStore.data.map { it[KEY_NNM_ENABLED] ?: false }
    val tpbEnabled: Flow<Boolean>       = context.dataStore.data.map { it[KEY_TPB_ENABLED] ?: true }
    val newpipeEnabled: Flow<Boolean>   = context.dataStore.data.map { it[KEY_NEWPIPE_ENABLED] ?: false }

    val downloadLimitKb: Flow<Int>      = context.dataStore.data.map { (it[KEY_DL_LIMIT_KB] ?: 0).coerceAtLeast(0) }
    val uploadLimitKb: Flow<Int>        = context.dataStore.data.map { (it[KEY_UL_LIMIT_KB] ?: 0).coerceAtLeast(0) }
    val downloadLimitEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DL_LIMIT_ON] ?: false }
    val uploadLimitEnabled: Flow<Boolean>   = context.dataStore.data.map { it[KEY_UL_LIMIT_ON] ?: false }
    val maxActiveDownloads: Flow<Int>   = context.dataStore.data.map { it[KEY_MAX_ACTIVE] ?: 3 }

    val mediaFolders: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_MEDIA_FOLDERS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun setDarkTheme(v: Boolean)        = context.dataStore.edit { it[KEY_DARK_THEME] = v }
    suspend fun setAutoUpdateCheck(v: Boolean)   = context.dataStore.edit { it[KEY_AUTO_UPDATE] = v }
    suspend fun setDownloadPath(v: String)       = context.dataStore.edit { it[KEY_DOWNLOAD_PATH] = v }
    suspend fun setRuTorEnabled(v: Boolean)      = context.dataStore.edit { it[KEY_RUTOR_ENABLED] = v }
    suspend fun setRuTrackerEnabled(v: Boolean)  = context.dataStore.edit { it[KEY_RUTRACKER_ENABLED] = v }
    suspend fun setKinozalEnabled(v: Boolean)    = context.dataStore.edit { it[KEY_KINOZAL_ENABLED] = v }
    suspend fun setNnmEnabled(v: Boolean)        = context.dataStore.edit { it[KEY_NNM_ENABLED] = v }
    suspend fun setTpbEnabled(v: Boolean)        = context.dataStore.edit { it[KEY_TPB_ENABLED] = v }
    suspend fun setNewpipeEnabled(v: Boolean)    = context.dataStore.edit { it[KEY_NEWPIPE_ENABLED] = v }

    suspend fun setDownloadLimitKb(v: Int)   = context.dataStore.edit { it[KEY_DL_LIMIT_KB] = v.coerceAtLeast(0) }
    suspend fun setUploadLimitKb(v: Int)     = context.dataStore.edit { it[KEY_UL_LIMIT_KB] = v.coerceAtLeast(0) }
    suspend fun setDownloadLimitEnabled(v: Boolean) = context.dataStore.edit { it[KEY_DL_LIMIT_ON] = v }
    suspend fun setUploadLimitEnabled(v: Boolean)   = context.dataStore.edit { it[KEY_UL_LIMIT_ON] = v }
    suspend fun setMaxActiveDownloads(v: Int) = context.dataStore.edit { it[KEY_MAX_ACTIVE] = v.coerceIn(1, 20) }

    suspend fun addMediaFolder(path: String) = context.dataStore.edit { prefs ->
        val cur = prefs[KEY_MEDIA_FOLDERS]?.split("|")?.filter { it.isNotBlank() }?.toMutableList()
            ?: mutableListOf()
        if (!cur.contains(path)) { cur.add(path); prefs[KEY_MEDIA_FOLDERS] = cur.joinToString("|") }
    }

    suspend fun removeMediaFolder(path: String) = context.dataStore.edit { prefs ->
        val cur = prefs[KEY_MEDIA_FOLDERS]?.split("|")?.filter { it.isNotBlank() && it != path }
            ?: emptyList()
        prefs[KEY_MEDIA_FOLDERS] = cur.joinToString("|")
    }
}
