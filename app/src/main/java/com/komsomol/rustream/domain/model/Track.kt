package com.komsomol.rustream.domain.model

data class Track(
    val path: String,
    val fileName: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long = 0L
)
