package com.nuvio.tv.data.mediaserver

// Shelves are exclusive. Original multi-genre metadata remains on the detail page.
internal fun primaryServerGenre(title: ServerTitle, kind: ServerMediaKind): String {
    val genres = title.preview.genres.map(::canonicalServerGenre).distinct()
    if (kind == ServerMediaKind.SERIES) {
        reviewedSeriesGenres[title.externalIds.tmdb?.toString()]?.let { return if (it == "adventure") "action" else it }
        if (title.preview.name.equals("Zmiennicy", true)) return "comedy"
    }
    val usable = genres.filterNot { it in setOf("mini-series", "mini series", "anime", "children", "sport", "noir", "świąteczny") }
    if (kind == ServerMediaKind.MOVIE) {
        if ("animation" in usable) return "animation"
        if ("western" in usable) return "western"
        if ("war" in usable && usable.firstOrNull() != "documentary") return "war"
        if ("horror" in usable && usable.firstOrNull() in setOf("drama", "thriller", "mystery")) return "horror"
        if ("science fiction" in usable && usable.firstOrNull() in setOf("action", "adventure", "drama", "thriller")) return "science fiction"
    }
    // Reality on a feature film is usually an imported tag, not its narrative genre.
    val candidates = usable.filterNot { kind == ServerMediaKind.MOVIE && it == "reality" }
    val first = candidates.firstOrNull() ?: return missingMetadataGenre(title.preview.name)
    if (first == "drama") return candidates.firstOrNull { it in setOf("crime", "thriller", "romance", "history", "music") } ?: first
    return if (kind == ServerMediaKind.SERIES && first == "war") "history" else first
}

private fun missingMetadataGenre(name: String): String = when {
    name.equals("Awaria", true) || name.startsWith("Czarodziejskie trojkaty", true) ||
        name.startsWith("Straszydla", true) || name.startsWith("Detektyw Łodyga", true) ||
        name.startsWith("Niesamowite przygody Skarpetek", true) -> "animation"
    name.contains("Something in the Dirt", true) -> "science fiction"
    name.contains("Czarnoksieznik", true) -> "fantasy"
    name.contains("There Are No Saints", true) || name.equals("Uncommon Valor", true) -> "action"
    name.startsWith("Tede", true) -> "music"
    else -> "drama"
}
