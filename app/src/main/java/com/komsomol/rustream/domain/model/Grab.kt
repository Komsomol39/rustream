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

enum class GrabState { RESOLVING, DOWNLOADING, PROCESSING, DONE, ERROR }

// Один выбираемый вариант качества (плитка)
data class GrabFormat(
    val formatId: String,   // то, что уйдёт в yt-dlp -f (напр. "137+bestaudio" или "140")
    val label: String,      // «1080p60 • MP4» или «MP3 • 256 kbps»
    val detail: String?,    // «~250 МБ», иногда кодек
    val video: Boolean
)

data class GrabDownload(
    val id: String,
    val title: String,
    val video: Boolean,
    val progress: Float = 0f,
    val state: GrabState = GrabState.RESOLVING,
    val message: String? = null,
    val detail: String? = null   // «42% • 120,5 МБ • 2,3 МБ/с • осталось 0:42»
)

// Запрос форматов: пока yt-dlp опрашивает сайт (RESOLVING),
// затем список плиток (READY) или ошибка
data class FormatQuery(
    val url: String,
    val title: String,
    val loading: Boolean = true,
    val formats: List<GrabFormat> = emptyList(),
    val error: String? = null
)
