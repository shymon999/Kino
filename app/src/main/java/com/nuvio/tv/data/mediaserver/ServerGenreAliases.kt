package com.nuvio.tv.data.mediaserver

import java.util.Locale

// Keep server IDs intact. Group aliases and the server's combined Sci-Fi label locally.
fun canonicalServerGenre(name: String): String {
    val key = name.trim().lowercase(Locale.ROOT).replace('-', ' ').replace(Regex("\\s+"), " ")
    return when (key) {
        "akcja", "action", "action & adventure", "action and adventure", "akcja i przygoda" -> "action"
        "przygodowy", "przygodowe", "przygoda", "adventure" -> "adventure"
        "animacja", "animowane", "animation" -> "animation"
        "komedia", "komedie", "comedy" -> "comedy"
        "kryminał", "kryminalny", "kryminały", "crime" -> "crime"
        "dokument", "dokumentalny", "dokumentalne", "documentary" -> "documentary"
        "dramat", "drama" -> "drama"
        "familijny", "familijne", "rodzinny", "rodzinne", "family" -> "family"
        "children", "kids", "dzieci", "dziecięcy", "dzieciece", "dziecięce", "dla dzieci" -> "children"
        "thriller", "thrillery", "suspense", "suspens" -> "thriller"
        "historyczny", "historyczne", "history" -> "history"
        "muzyczny", "muzyczne", "muzyka", "music" -> "music"
        "tajemnica", "mystery" -> "mystery"
        "romans", "romance" -> "romance"
        "sport", "sports", "sportowy", "sportowe" -> "sport"
        "sci fi", "scifi", "science fiction", "science ficiton", "fantastyka naukowa",
        "sci fi & fantasy", "sci fi and fantasy", "sci fi i fantasy",
        "science fiction & fantasy", "science fiction and fantasy", "science fiction i fantasy" -> "science fiction"
        "wojenny", "wojenne", "war", "war & politics", "war and politics", "wojna i polityka" -> "war"
        "biografia", "biograficzny", "biography" -> "biography"
        else -> key
    }
}

internal fun ServerLibrary.groupedGenres(): List<List<ServerGenre>> =
    genres.groupBy { canonicalServerGenre(it.name) }.values.map { group -> group.sortedBy { it.id } }
