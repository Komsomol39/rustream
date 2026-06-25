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

        val ruTorDeferred = if (ruTorEnabled)
            async { runCatching { ruTorProvider.search(query, category) }.getOrElse { emptyList() } }
        else null

        val ruTrackerDeferred = if (ruTrackerEnabled && ruTrackerProvider.isLoggedIn())
            async { runCatching { ruTrackerProvider.search(query, category) }.getOrElse { emptyList() } }
        else null

        val results = mutableListOf<SearchResult>()
        ruTorDeferred?.let { results.addAll(it.await()) }
        ruTrackerDeferred?.let { results.addAll(it.await()) }
        results.sortedByDescending { it.seeders }
    }
}
