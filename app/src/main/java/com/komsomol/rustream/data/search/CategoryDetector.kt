package com.komsomol.rustream.data.search

import com.komsomol.rustream.domain.model.ContentCategory

object CategoryDetector {

    // Явные НЕ-медиа ключевые слова — если есть, сразу не музыка и не видео
    private val EXCLUDE_PATTERNS = listOf(
        "windows", "office", "microsoft", "adobe", "autocad", "photoshop",
        "программ", "software", "repack", "portable", "игр", "game", "games",
        "android apk", "linux", "macos", "driver", "firmware"
    )

    private val MUSIC_PATTERNS = listOf(
        // Форматы
        "mp3", "flac", "wav", "aac", "ogg", "opus", "ape", " wv ", "m4a",
        "lossless", "hi-res", "hires", "dsd", "sacd", "vinyl",
        // Типы релизов
        "альбом", "дискограф", "сингл", "ost ", " ost]", "[ost]",
        "discography", "album", "single", " ep ", " ep]", "[ep]",
        "soundtrack",
        // Теги RuTracker/NNM в скобках — жанры в начале строки
        "(rap", "(hip-hop", "(hip hop", "(rock", "(pop", "(jazz", "(blues",
        "(metal", "(punk", "(reggae", "(electronic", "(techno", "(house",
        "(trance", "(dnb", "(drum", "(ambient", "(folk", "(r&b", "(soul",
        "(country", "(indie", "(alternative", "(classical", "(opera",
        "(хип-хоп", "(рок", "(поп", "(джаз", "(метал", "(электрон",
        // Без скобок
        "rap,", "hip-hop,", "rock,", "pop,", "jazz,",
    )

    private val VIDEO_PATTERNS = listOf(
        // Форматы контейнеров
        "mkv", "avi", " mp4", ".mp4", "mov", "wmv", "m2ts",
        "blu-ray", "bluray", "bdrip", "bdremux", "bdrip",
        "hdtv", "webrip", "web-dl", "webdl", "hdrip", "dvdrip",
        // Разрешения
        "1080p", "720p", "2160p", "4k ", " 4k", "uhd",
        // Кодеки (только видео специфичные)
        "hevc", "x264", "x265", "h264", "h265", "h.264", "h.265",
        // Типы контента
        "фильм", "сериал", "movie", "film", "season", "episode",
        "мультфильм", "аниме", "anime", "documentary", "докумен",
        "сезон ", "серия ",
        // WEB теги
        "web-dlri", "bdrip", "webrip",
    )

    fun detect(title: String, category: String, requested: ContentCategory): ContentCategory {
        // Если запрос конкретной категории — используем серверную фильтрацию
        // но дополнительно проверяем что результат не противоречит
        val text = (title + " " + category).lowercase()

        // Явные исключения — это точно не медиа-контент
        val isExcluded = EXCLUDE_PATTERNS.any { text.contains(it) }
        if (isExcluded) {
            // Если пользователь просит музыку/видео — не показываем
            return if (requested == ContentCategory.ALL) ContentCategory.ALL
            else return ContentCategory.ALL  // вернём ALL чтобы SearchRepository отфильтровал
        }

        val isMusic = MUSIC_PATTERNS.any { text.contains(it) }
        val isVideo = VIDEO_PATTERNS.any { text.contains(it) }

        return when (requested) {
            ContentCategory.MUSIC -> {
                // Запросили музыку — показываем только если это музыка
                if (isMusic || (!isVideo)) ContentCategory.MUSIC
                else ContentCategory.VIDEO // пусть SearchRepository отфильтрует
            }
            ContentCategory.VIDEO -> {
                if (isVideo || (!isMusic)) ContentCategory.VIDEO
                else ContentCategory.MUSIC
            }
            ContentCategory.ALL -> when {
                isMusic && !isVideo -> ContentCategory.MUSIC
                isVideo && !isMusic -> ContentCategory.VIDEO
                isMusic -> ContentCategory.MUSIC
                else -> ContentCategory.ALL
            }
        }
    }
}
