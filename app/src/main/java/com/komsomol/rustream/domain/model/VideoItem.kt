package com.komsomol.rustream.domain.model

data class VideoItem(
    val path: String,
    val fileName: String,
    val title: String,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L
)
