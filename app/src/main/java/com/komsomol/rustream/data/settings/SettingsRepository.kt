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
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        val KEY_DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val KEY_RUTRACKER_LOGIN = stringPreferencesKey("rutracker_login")
        val KEY_RUTRACKER_PASSWORD = stringPreferencesKey("rutracker_password")
    }

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[KEY_DARK_THEME] ?: true }
    val downloadPath: Flow<String> = context.dataStore.data.map {
        it[KEY_DOWNLOAD_PATH] ?: "/sdcard/RuStream"
    }
    val ruTrackerLogin: Flow<String> = context.dataStore.data.map { it[KEY_RUTRACKER_LOGIN] ?: "" }
    val ruTrackerPassword: Flow<String> = context.dataStore.data.map { it[KEY_RUTRACKER_PASSWORD] ?: "" }

    suspend fun setDarkTheme(value: Boolean) {
        context.dataStore.edit { it[KEY_DARK_THEME] = value }
    }

    suspend fun setDownloadPath(value: String) {
        context.dataStore.edit { it[KEY_DOWNLOAD_PATH] = value }
    }

    suspend fun setRuTrackerLogin(value: String) {
        context.dataStore.edit { it[KEY_RUTRACKER_LOGIN] = value }
    }

    suspend fun setRuTrackerPassword(value: String) {
        context.dataStore.edit { it[KEY_RUTRACKER_PASSWORD] = value }
    }
}
