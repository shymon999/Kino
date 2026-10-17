package com.nuvio.tv.data.mediaserver.mediabrowser

import com.nuvio.tv.data.mediaserver.*
import com.nuvio.tv.data.mediaserver.emby.EmbyProvider
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FamilyEmbyCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val library = ServerLibrary("movies", "Filmy", ServerMediaKind.MOVIE)
    private val session = ServerSession(ServerConnection("c1", "emby", "Home", "https://media.example.com", "s1", "u1", "viewer", "k1",
        libraries = listOf(library)), "secret-token-not-for-disk")

    @Test fun restartFindsMisspelledActorLocallyWithoutAnyNetworkRequest() = runBlocking<Unit> {
        val disk = BoundedMetadataDiskCache(folder.newFolder())
        val http = TestHttp { request ->
            if (request.url.encodedPath.endsWith("/System/Info/Public")) """{"Version":"4.10.1.0"}"""
            else """{"TotalRecordCount":1,"Items":[{"Id":"one","Name":"Terminator","Type":"Movie","OriginalTitle":"The Terminator",
                "ProviderIds":{"Imdb":"tt0088247","Tmdb":"218"},"Genres":["Science Fiction"],
                "ImageTags":{"Primary":"tag"},"People":[{"Name":"Arnold Schwarzenegger","Type":"Actor"}]}]}"""
        }
        val first = EmbyProvider(http.client, testIdentity, disk)
        val results = first.search(session, ServerMediaKind.MOVIE, listOf(library), "nold schwarezenegger", 100)
        assertEquals("Terminator", results.single().preview.name)
        assertFalse(http.requests.any { it.url.queryParameter("searchTerm") != null })
        val offline = TestHttp { throw IOException("Network disabled for restart test") }
        val restarted = EmbyProvider(offline.client, testIdentity, disk)
        val saved = restarted.search(session, ServerMediaKind.MOVIE, listOf(library), "nold schwarezenegger", 100).single()
        assertEquals(results.single(), saved)
        assertEquals("tt0088247", saved.externalIds.imdb)
        assertEquals(0, offline.requests.size)
        assertTrue(disk.sizeBytes() < 1024 * 1024)
    }

    @Test fun detailsAndFilmographySurviveRestartAndRespectSelectedLibraries() = runBlocking<Unit> {
        val disk = BoundedMetadataDiskCache(folder.newFolder())
        val http = TestHttp { request ->
            when {
                request.url.encodedPath.endsWith("/person") -> """{"Id":"person","Name":"Bruce Willis","Type":"Person","Overview":"Biography"}"""
                request.url.queryParameter("personIds") != null -> """{"TotalRecordCount":1,"Items":[{"Id":"film","Name":"Szklana pułapka","Type":"Movie"}]}"""
                else -> """{"Id":"film","Name":"Szklana pułapka","Type":"Movie","Genres":["Action"],"People":[{"Id":"person","Name":"Bruce Willis","Type":"Actor"}]}"""
            }
        }
        val first = EmbyProvider(http.client, testIdentity, disk)
        val details = first.details(session, "film")
        val person = first.personDetails(session, "person")
        val offline = TestHttp { throw IOException("Network disabled") }
        val restarted = EmbyProvider(offline.client, testIdentity, disk)
        assertEquals(details.copy(userStates = emptyList()), restarted.details(session, "film"))
        assertEquals(person, restarted.personDetails(session, "person"))
        assertTrue(offline.requests.isEmpty())
        val changedLibraries = ServerSession(session.connection.copy(libraries = listOf(library.copy(id = "other"))), session.token)
        assertTrue(runCatching { restarted.personDetails(changedLibraries, "person") }.isFailure)
        assertTrue(offline.requests.isNotEmpty())
    }
}
