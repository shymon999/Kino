package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.core.tracking.TrackingExternalIds
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.*
import org.junit.Test

class ServerPrimaryGenreTest {
    private fun title(name: String, genres: List<String>, tmdb: Long? = null) = ServerTitle(
        MetaPreview("test", ContentType.MOVIE, name = name, poster = null, posterShape = PosterShape.POSTER,
            background = null, logo = null, description = null, releaseInfo = null, imdbRating = null, genres = genres),
        TrackingExternalIds(tmdb = tmdb))

    @Test fun narrativeGenresReplaceIncorrectRealityAndOverlyBroadDrama() {
        assertEquals("war", primaryServerGenre(title("13 godzin", listOf("Reality", "Akcja", "Wojenny", "Historyczny")), ServerMediaKind.MOVIE))
        assertEquals("crime", primaryServerGenre(title("Dexter", listOf("Drama", "Crime", "Thriller")), ServerMediaKind.SERIES))
        assertEquals("comedy", primaryServerGenre(title("Mecenas She-Hulk", listOf("Science Fiction", "Drama", "Comedy"), 92783), ServerMediaKind.SERIES))
    }

    @Test fun animationIsNotScatteredAcrossAdventureAndComedyShelves() {
        val item = title("12 prac Asteriksa", listOf("Familijny", "Animacja", "Komedia", "Przygodowy"))
        assertEquals("animation", primaryServerGenre(item, ServerMediaKind.MOVIE))
        assertEquals(4, item.preview.genres.size)
    }
}
