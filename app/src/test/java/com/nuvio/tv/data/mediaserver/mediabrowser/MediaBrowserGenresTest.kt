package com.nuvio.tv.data.mediaserver.mediabrowser

import com.nuvio.tv.data.mediaserver.ServerConnection
import com.nuvio.tv.data.mediaserver.ServerLibrary
import com.nuvio.tv.data.mediaserver.ServerMediaKind
import com.nuvio.tv.data.mediaserver.ServerSession
import com.nuvio.tv.data.mediaserver.emby.EmbyProvider
import com.nuvio.tv.data.mediaserver.jellyfin.JellyfinProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBrowserGenresTest {
    private fun session() = ServerSession(
        ServerConnection("c1", "emby", "Test", "https://media.example", "s1", "u1", "viewer", "k1"),
        "test-token"
    )

    @Test
    fun loadsLibraryScopedGenresForBothProvidersAndKeepsTheirNames() = runTest {
        val http = TestHttp { """{"Items":[{"Id":"g2","Name":"Science Fiction"},{"Id":"g1","Name":"Akcja"},{"Id":"g3","Name":""}],"TotalRecordCount":3}""" }
        for (provider in listOf(EmbyProvider(http.client, testIdentity), JellyfinProvider(http.client, testIdentity))) {
            val genres = provider.libraryGenres(session(), ServerLibrary("shows", "Seriale", ServerMediaKind.SERIES))
            assertEquals(listOf("Akcja", "Science Fiction"), genres.map { it.name })
            val request = http.requests.last()
            assertEquals("shows", request.url.queryParameter("parentId"))
            assertEquals("u1", request.url.queryParameter("userId"))
            assertEquals("Series", request.url.queryParameter("includeItemTypes"))
            assertEquals("true", request.url.queryParameter("recursive"))
            assertTrue(request.url.encodedPath.endsWith("/Genres"))
        }
    }

    @Test
    fun paginatesGenreListsAndStopsWhenAServerRepeatsAPage() = runTest {
        val http = TestHttp { request ->
            val start = request.url.queryParameter("startIndex")?.toInt() ?: 0
            if (start == 0) """{"Items":[{"Id":"g1","Name":"Akcja"}],"TotalRecordCount":2}"""
            else """{"Items":[{"Id":"g2","Name":"Komedia"}],"TotalRecordCount":2}"""
        }
        val provider = EmbyProvider(http.client, testIdentity)
        val library = ServerLibrary("movies", "Filmy", ServerMediaKind.MOVIE)
        assertEquals(2, provider.libraryGenres(session(), library).size)
        assertEquals(listOf("0", "1"), http.requests.map { it.url.queryParameter("startIndex") })
        val repeating = TestHttp { """{"Items":[{"Id":"g1","Name":"Akcja"}],"TotalRecordCount":999}""" }
        assertEquals(1, EmbyProvider(repeating.client, testIdentity).libraryGenres(session(), library).size)
        assertEquals(2, repeating.requests.size)
    }

    @Test
    fun embyRandomOrderUsesTheSameSeedAcrossPagesAndCombinedGenres() = runTest {
        val http = TestHttp { """{"Items":[],"TotalRecordCount":0}""" }
        val provider = EmbyProvider(http.client, testIdentity)
        val library = ServerLibrary("movies", "Filmy", ServerMediaKind.MOVIE)
        provider.libraryPage(session(), library, 0, 50, "g1,g2")
        provider.libraryPage(session(), library, 50, 50, "g1,g2")
        assertEquals(listOf("0", "50"), http.requests.map { it.url.queryParameter("startIndex") })
        val seeds = http.requests.map { it.url.queryParameter("randomSeed") }
        assertTrue(!seeds.first().isNullOrBlank())
        assertEquals(seeds.first(), seeds.last())
        http.requests.forEach {
            assertEquals("Random", it.url.queryParameter("sortBy"))
            assertEquals("g1,g2", it.url.queryParameter("genreIds"))
        }
    }

    @Test
    fun toleratesExplicitNullCollectionsFromServerJson() = runTest {
        val http = TestHttp { """{"Items":[{"Id":"1","Name":"Film","Type":"Movie","Genres":null,"ProviderIds":null,"ImageTags":null,"People":null}],"TotalRecordCount":1}""" }
        val provider = EmbyProvider(http.client, testIdentity)
        val page = provider.libraryPage(session(), ServerLibrary("movies", "Filmy", ServerMediaKind.MOVIE), 0, 50, "g1")
        assertEquals(1, page.items.size)
        assertTrue(page.items.single().preview.genres.isEmpty())
    }

    @Test
    fun emptyExplicitGenreNeverFallsBackToAnotherGenre() = runTest {
        val http = TestHttp { """{"Items":[],"TotalRecordCount":0}""" }
        val provider = EmbyProvider(http.client, testIdentity)
        val library = ServerLibrary("movies", "Filmy", ServerMediaKind.MOVIE)
        assertTrue(provider.libraryPage(session(), library, 0, 50, "g-comedy").items.isEmpty())
        provider.libraryPage(session(), library, 50, 50, "g-comedy")
        assertEquals(2, http.requests.size)
        http.requests.forEach { request ->
            assertEquals("g-comedy", request.url.queryParameter("genreIds"))
            assertEquals("movies", request.url.queryParameter("parentId"))
            assertEquals("Movie", request.url.queryParameter("includeItemTypes"))
            assertTrue(request.url.encodedPath.endsWith("/Items"))
        }
    }
}
