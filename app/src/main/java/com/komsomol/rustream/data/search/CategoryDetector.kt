package com.komsomol.rustream.data.search

import com.komsomol.rustream.domain.model.ContentCategory

object CategoryDetector {

    // Музыкальные жанры и форматы — встречаются в названиях RuTracker/RuTor
    private val MUSIC_PATTERNS = listOf(
        // Форматы
        "mp3", "flac", "wav", "aac", "ogg", "opus", "ape", "wv", "m4a",
        "lossless", "hi-res", "hires", "dsd", "sacd", "cd-r", "vinyl",
        // Типы релизов
        "альбом", "дискограф", "сингл", "трек", "soundtrack", "ost",
        "discography", "album", "single", "ep ", " ep]", "[ep]",
        // Жанры
        "mp3", "rap", "hip-hop", "hip hop", "хип-хоп", "хип хоп",
        "rock", "рок", "pop", "поп", "jazz", "джаз", "blues", "блюз",
        "electronic", "электрон", "techno", "house", "trance", "dnb",
        "drum and bass", "drum'n'bass", "ambient", "folk", "фолк",
        "metal", "металл", "punk", "панк", "reggae", "регги",
        "classical", "классич", "opera", "опера", "r&b", "soul",
        "country", "кантри", "indie", "инди", "alternative",
        // Теги RuTracker в скобках
        "rap,", "hip-hop,", "rock,", "pop,", "jazz,", "blues,",
        "metal,", "punk,", "electronic,", "techno,", "house,",
    )

    private val VIDEO_PATTERNS = listOf(
        // Форматы
        "mkv", "avi", "mp4", "mov", "wmv", "m2ts", "ts ", ".ts",
        "blu-ray", "bluray", "bdrip", "bdremux", "bdrip",
        "hdtv", "webrip", "web-dl", "webdl", "hdrip", "dvdrip",
        "1080p", "720p", "2160p", "4k ", " 4k", "uhd",
        "hevc", "x264", "x265", "h264", "h265",
        // Типы контента
        "фильм", "сериал", "movie", "series", "season", "episode",
        "мультфильм", "аниме", "anime", "documentary", "докумен",
        "сезон", "серия",
    )

    fun detect(title: String, category: String, requested: ContentCategory): ContentCategory {
        if (requested != ContentCategory.ALL) return requested

        val text = (title + " " + category).lowercase()

        // Сначала проверяем видео — более специфичные паттерны
        val isVideo = VIDEO_PATTERNS.any { text.contains(it) }
        val isMusic = MUSIC_PATTERNS.any { text.contains(it) }

        return when {
            isMusic && !isVideo -> ContentCategory.MUSIC
            isVideo && !isMusic -> ContentCategory.VIDEO
            // Оба или ни одного — смотрим на категорию RuTracker
            isMusic -> ContentCategory.MUSIC  // музыка с тегами жанра — скорее музыка
            else -> ContentCategory.ALL
        }
    }
}
