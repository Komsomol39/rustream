package com.komsomol.rustream.domain.model

data class SearchResult(
    val title: String,
    val source: SearchSource,
    val category: ContentCategory,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int,
    val magnetUri: String?,
    val torrentUrl: String?,
    val detailUrl: String,
    val uploadDate: String = ""
)

enum class SearchSource(val displayName: String) {
    RUTOR("RuTor"),
    RUTRACKER("RuTracker"),
    KINOZAL("Kinozal"),
    NNM("NNM-Club"),
    YTS("YTS")
}

enum class ContentCategory(val displayName: String) {
    VIDEO("Видео"),
    MUSIC("Музыка"),
    ALL("Все")
}
