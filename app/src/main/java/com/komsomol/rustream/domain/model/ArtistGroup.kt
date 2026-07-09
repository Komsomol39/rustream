package com.komsomol.rustream.domain.model

data class ArtistGroup(
    val displayName: String,        // отображаемое имя (canonical)
    val memberNames: List<String>,  // какие "сырые" имена исполнителей входят
    val tracks: List<Track>
)
