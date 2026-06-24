package com.komsomol.rustream.data.search

import com.komsomol.rustream.data.settings.SettingsRepository
import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val ruTorProvider: RuTorProvider,
    private val ruTrackerProvider: RuTrackerProvider,
    private val settings: SettingsRepository
) {
    suspend fun search(query: String, category: ContentCategory): List<SearchResult> = coroutineScope {
        val ruTorEnabled     = settings.ruTorEnabled.first()
        val ruTrackerEnabled = settings.ruTrackerEnabled.first()
        val rtLogin          = settings.ruTrackerLogin.first()
        val rtPassword       = settings.ruTrackerPassword.first()

        if (ruTrackerEnabled && rtLogin.isNotBlank()) {
            ruTrackerProvider.setCredentials(rtLogin, rtPassword)
        }

        val ruTorDeferred     = if (ruTorEnabled) async { ruTorProvider.search(query, category) } else null
        val ruTrackerDeferred = if (ruTrackerEnabled && rtLogin.isNotBlank()) async { ruTrackerProvider.search(query, category) } else null

        val allResults = mutableListOf<SearchResult>()
        ruTorDeferred?.let {
            try { allResults.addAll(it.await()) } catch (_: Exception) {}
        }
        ruTrackerDeferred?.let {
            try { allResults.addAll(it.await()) } catch (_: Exception) {}
        }

        // Сортируем по сидам
        allResults.sortedByDescending { it.seeders }
    }
}
