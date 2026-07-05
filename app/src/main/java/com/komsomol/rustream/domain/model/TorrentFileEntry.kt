package com.komsomol.rustream.domain.model

data class TorrentFileEntry(
    val index: Int,
    val name: String,
    val sizeBytes: Long,
    val downloadedBytes: Long,
    val enabled: Boolean
)
