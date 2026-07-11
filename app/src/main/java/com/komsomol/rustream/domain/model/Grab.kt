package com.komsomol.rustream.domain.model

data class GrabResult(
    val serviceId: Int,
    val serviceName: String,
    val url: String,
    val title: String,
    val uploader: String? = null,
    val durationSec: Long = 0L,
    val thumbnailUrl: String? = null
)

enum class GrabState { RESOLVING, DOWNLOADING, DONE, ERROR }

data class GrabDownload(
    val id: String,
    val title: String,
    val video: Boolean,
    val progress: Float = 0f,
    val state: GrabState = GrabState.RESOLVING,
    val message: String? = null,
    val detail: String? = null   // «42% • 120,5 МБ • 2,3 МБ/с • осталось 0:42»
)
