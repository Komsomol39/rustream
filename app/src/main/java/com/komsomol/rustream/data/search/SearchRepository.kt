package com.komsomol.rustream.data.search

import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val ruTorProvider: RuTorProvider
) {
    suspend fun search(query: String, category: ContentCategory): List<SearchResult> {
        return ruTorProvider.search(query, category)
    }
}
