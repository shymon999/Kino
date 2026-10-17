package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.data.mediaserver.emby.FamilySearchIndex
import com.nuvio.tv.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FamilySearchIndexTest {
    private fun title(name: String, vararg people: String) = ServerTitle(MetaPreview(
        name, ContentType.MOVIE, name = name, poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = null, releaseInfo = null, imdbRating = null,
        genres = emptyList()), peopleNames = people.toList())

    @Test fun vocabularyFilterNeverDropsValidFuzzyMatchesAndKeepsOrder() = runBlocking {
        val titles = listOf(title("Sabotaż", "Arnold Schwarzenegger"), title("Szklana pułapka", "Bruce Willis"),
            title("Pamięć absolutna", "Arnold Schwarzenegger"), title("Strefa gangsterów", "Tom Hardy"),
            title("Ziemia obiecana", "Daniel Olbrychski"), title("Człowiek z wysokiego zamku"), title("Nowy świat"))
        val first = FamilySearchIndex(titles)
        val restarted = FamilySearchIndex(titles, first.words)
        for (query in listOf("nold schwarezenegger", "szklanka pulapa", "Ziemia gangsterów", "Bruce Willis", "Bruec Wills",
            "Czlowiek z wysokiego zamku", "Nowy świat", "abcdefgh xyz", "Arnold", "schwrazeneggre")) {
            val reference = ServerSearchMatcher(query)
            val matching = titles.filter { reference.score(it.preview.name) > 0 || it.peopleNames.any { name -> reference.score(name, true) > 0 } }
            val candidates = restarted.candidates(query)
            assertTrue("Missing candidate for $query", candidates.containsAll(matching))
            assertEquals(titles.filter { it in candidates }, candidates)
        }
        assertEquals(listOf(titles[0], titles[2]), restarted.candidates("nold schwarezenegger"))
        assertTrue(restarted.candidates("abcdefgh xyz").isEmpty())
    }
}
