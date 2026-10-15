package com.nuvio.tv.data.mediaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerGenreAliasesTest {
    @Test
    fun groupsEquivalentNamesAcrossLanguagesAndSpelling() {
        assertEquals(canonicalServerGenre("Sci-Fi"), canonicalServerGenre("Science Fiction"))
        assertEquals(canonicalServerGenre("Family"), canonicalServerGenre("Familijny"))
        assertEquals(canonicalServerGenre("Action"), canonicalServerGenre("Akcja"))
        assertEquals(canonicalServerGenre(" COMEDY "), canonicalServerGenre("Komedia"))
    }

    @Test
    fun retainsDistinctGenresAndUnknownNames() {
        assertNotEquals(canonicalServerGenre("Fantasy"), canonicalServerGenre("Science Fiction"))
        assertNotEquals(canonicalServerGenre("Family"), canonicalServerGenre("Kids"))
        assertEquals("lokalny gatunek", canonicalServerGenre("Lokalny gatunek"))
    }
}
