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
    private val kinozalProvider: KinozalProvider,
    private val nnmProvider: NnmClubProvider,
    private val settings: SettingsRepository
) {
    suspend fun search(query: String, category: ContentCategory): List<SearchResult> = coroutineScope {
        val ruTorEnabled     = settings.ruTorEnabled.first()
        val ruTrackerEnabled = settings.ruTrackerEnabled.first()
        val kinozalEnabled   = settings.kinozalEnabled.first()
        val nnmEnabled       = settings.nnmEnabled.first()

        val d1 = if (ruTorEnabled)
            async { runCatching { ruTorProvider.search(query, category) }.getOrElse { emptyList() } } else null
        val d2 = if (ruTrackerEnabled && ruTrackerProvider.isLoggedIn())
            async { runCatching { ruTrackerProvider.search(query, category) }.getOrElse { emptyList() } } else null
        val d3 = if (kinozalEnabled)
            async { runCatching { kinozalProvider.search(query, category) }.getOrElse { emptyList() } } else null
        val d4 = if (nnmEnabled)
            async { runCatching { nnmProvider.search(query, category) }.getOrElse { emptyList() } } else null

        val results = mutableListOf<SearchResult>()
        d1?.let { results.addAll(it.await()) }
        d2?.let { results.addAll(it.await()) }
        d3?.let { results.addAll(it.await()) }
        d4?.let { results.addAll(it.await()) }
        results.sortedByDescending { it.seeders }
    }
}
