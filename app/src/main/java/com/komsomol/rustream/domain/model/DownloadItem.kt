package com.komsomol.rustream.domain.model

data class DownloadItem(
    val id: String,                    // уникальный ID (хэш magnet или UUID)
    val title: String,
    val magnetUri: String?,
    val torrentUrl: String?,
    val savePath: String,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Float = 0f,          // 0.0 - 1.0
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeedBps: Long = 0L,   // байт/сек
    val uploadSpeedBps: Long = 0L,
    val seeds: Int = 0,
    val peers: Int = 0,
    val errorMessage: String? = null,  // текст ошибки для UI
    val addedAt: Long = System.currentTimeMillis()
)

enum class DownloadState {
    QUEUED,       // в очереди
    FETCHING_META,// получаем метаданные (magnet)
    DOWNLOADING,  // скачивается
    PAUSED,       // на паузе
    FINISHED,     // завершено
    ERROR         // ошибка
}
