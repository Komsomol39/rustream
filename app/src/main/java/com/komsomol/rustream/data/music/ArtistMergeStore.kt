package com.komsomol.rustream.data.music

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.komsomol.rustream.data.settings.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// Правила: сырое имя исполнителя -> каноничное имя группы.
// Хранится как JSON в DataStore, переживает перезапуск.
@Singleton
class ArtistMergeStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY = stringPreferencesKey("artist_merge_map")

    val mergeMap: Flow<Map<String, String>> =
        context.dataStore.data.map { prefs -> parse(prefs[KEY]) }

    private fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } }
        } catch (_: Exception) { emptyMap() }
    }

    private suspend fun save(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        context.dataStore.edit { it[KEY] = o.toString() }
    }

    // Объединить несколько имён под одним каноничным (берём первое как имя группы)
    suspend fun merge(names: List<String>) {
        if (names.size < 2) return
        val canonical = names.first()
        val current = parse(context.dataStore.data.map { it[KEY] }.let {
            var r: Map<String, String> = emptyMap()
            kotlinx.coroutines.flow.first(it) { true }.let { p -> r = parse(p) }
            r
        }.toString())
        // проще: перечитать напрямую
        val map = readNow().toMutableMap()
        // если canonical сам был участником другой группы — используем его группу
        val realCanonical = map[canonical] ?: canonical
        names.forEach { map[it] = realCanonical }
        save(map)
    }

    // Разгруппировать одно имя (убрать правило)
    suspend fun unmerge(name: String) {
        val map = readNow().toMutableMap()
        map.remove(name)
        save(map)
    }

    private suspend fun readNow(): Map<String, String> {
        var result: Map<String, String> = emptyMap()
        kotlinx.coroutines.flow.collect(
            kotlinx.coroutines.flow.take(context.dataStore.data, 1)
        ) { result = parse(it[KEY]) }
        return result
    }
}
