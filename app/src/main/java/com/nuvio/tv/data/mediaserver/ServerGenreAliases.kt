package com.nuvio.tv.data.mediaserver

import java.util.Locale

// Keep server IDs intact. Only equivalent genre names share a display row and filter.
fun canonicalServerGenre(name: String): String {
    val key = name.trim().lowercase(Locale.ROOT).replace('-', ' ').replace(Regex("\\s+"), " ")
    return when (key) {
        "akcja", "action" -> "action"
        "przygodowy", "przygodowe", "przygoda", "adventure" -> "adventure"
        "animacja", "animowane", "animation" -> "animation"
        "komedia", "komedie", "comedy" -> "comedy"
        "kryminał", "kryminalny", "kryminały", "crime" -> "crime"
        "dokument", "dokumentalny", "dokumentalne", "documentary" -> "documentary"
        "dramat", "drama" -> "drama"
        "familijny", "familijne", "rodzinny", "rodzinne", "family" -> "family"
        "historyczny", "historyczne", "history" -> "history"
        "muzyczny", "muzyczne", "muzyka", "music" -> "music"
        "tajemnica", "mystery" -> "mystery"
        "romans", "romance" -> "romance"
        "sci fi", "scifi", "science fiction", "science ficiton", "fantastyka naukowa" -> "science fiction"
        "wojenny", "wojenne", "war" -> "war"
        "biografia", "biograficzny", "biography" -> "biography"
        else -> key
    }
}

internal fun ServerLibrary.groupedGenres(): List<List<ServerGenre>> =
    genres.groupBy { canonicalServerGenre(it.name) }.values.map { group -> group.sortedBy { it.id } }
