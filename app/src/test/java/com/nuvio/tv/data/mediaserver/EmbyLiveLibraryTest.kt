package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.data.mediaserver.emby.EmbyProvider
import com.nuvio.tv.data.mediaserver.mediabrowser.ServerClientIdentity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

/** Explicitly opt-in metadata-only integration test using an isolated temporary session. */
class EmbyLiveLibraryTest {
    @Test fun realLibraryFuzzySearchActorsAndExclusiveShelves() = runBlocking<Unit> {
        val path = System.getenv("NUVIO_EMBY_TEST_CONFIG")
        assumeNotNull(path)
        val configuration = File(path!!).readText()
        fun field(name: String): String {
            val literal = Regex("String\\s+$name\\s*=\\s*(\"(?:\\\\.|[^\"\\\\])*\");")
                .find(configuration)?.groupValues?.get(1) ?: error("Missing test configuration field")
            return Json.parseToJsonElement(literal).jsonPrimitive.content
        }
        val device = "kino-metadata-test-${UUID.randomUUID()}"
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Nuvio-Emby-QA/1.1").build())
        }.build()
        val provider = EmbyProvider(http, ServerClientIdentity("Metadata integration test", "1.1") { device })
        val login = provider.signIn(field("FAMILY_EMBY_URL"), field("FAMILY_EMBY_USERNAME"), field("FAMILY_EMBY_PASSWORD"))
        val draft = ServerConnection("test", "emby", "Test", login.address, login.serverId, login.userId,
            login.userName, "temporary-test")
        var session = ServerSession(draft, login.token)
        try {
            val available = provider.libraries(session).filter { it.name == "Filmy" || it.name == "Seriale" }
            val movieLibraries = available.filter { it.kind == ServerMediaKind.MOVIE }
            var searchStarted = System.nanoTime()
            val arnold = provider.search(session, ServerMediaKind.MOVIE, movieLibraries, "nold schwarezenegger", 100)
            val coldSearchMs = (System.nanoTime() - searchStarted) / 1_000_000
            assertTrue("Misspelled actor was not found", arnold.isNotEmpty())
            assertTrue("Unrelated actor results", arnold.all { it.peopleNames.contains("Arnold Schwarzenegger") })
            searchStarted = System.nanoTime()
            val warmArnold = provider.search(session, ServerMediaKind.MOVIE, movieLibraries, "nold schwarezenegger", 100)
            val warmSearchMs = (System.nanoTime() - searchStarted) / 1_000_000
            assertEquals(arnold.map { it.preview.id }, warmArnold.map { it.preview.id })
            val libraries = available
                .map { it.copy(genres = provider.libraryGenres(session, it)) }
            session = ServerSession(draft.copy(libraries = libraries), login.token)
            val movies = libraries.filter { it.kind == ServerMediaKind.MOVIE }
            val fuzzy = provider.search(session, ServerMediaKind.MOVIE, movies, "szklanka pulapa", 100)
            assertTrue("Dictated title was not found", fuzzy.any { it.preview.name.startsWith("Szklana pułapka") })
            val actor = provider.search(session, ServerMediaKind.MOVIE, movies, "Bruce Willis", 100)
            assertTrue("Incomplete actor search", actor.size >= 38)
            assertTrue("Unexpected results: " + actor.filterNot { it.peopleNames.contains("Bruce Willis") }.joinToString { it.preview.name + " " + it.peopleNames.filter { name -> name.startsWith("Bruce") } }, actor.all { it.peopleNames.contains("Bruce Willis") })
            // The fifth film currently has one incomplete credit on this server. Use a known
            // complete film for the person-navigation check, independently of shuffled results.
            val firstFilm = ServerItemRef.parse(fuzzy.first { it.preview.name == "Szklana pułapka" }.preview.id)!!
            val cast = provider.details(session, firstFilm.itemId).meta.castMembers
            val person = ServerItemRef.parse(cast.first { it.name == "Bruce Willis" }.serverPersonId)!!
            val filmography = provider.personDetails(session, person.itemId)
            assertTrue(filmography.movieCredits.size >= 38)
            val report = buildJsonObject {
                put("misspelled_actor_results", arnold.size)
                put("misspelled_actor_cold_ms", coldSearchMs)
                put("misspelled_actor_warm_ms", warmSearchMs)
                put("fuzzy_title_results", fuzzy.size)
                put("actor_search_results", actor.size)
                put("actor_filmography_results", filmography.movieCredits.size)
                put("shelves", buildJsonObject {
                    for (library in libraries) {
                        val seen = mutableSetOf<String>()
                        put(library.name, buildJsonObject {
                            for (genre in library.genres) {
                                val titles = mutableListOf<ServerTitle>()
                                var offset = 0
                                while (true) {
                                    val page = provider.libraryPage(session, library, offset, 50, genre.id)
                                    titles += page.items
                                    offset += page.items.size
                                    if (page.items.isEmpty() || offset >= (page.totalCount ?: offset)) break
                                }
                                assertTrue("Tiny automatic shelf", titles.size >= 4)
                                assertTrue("Title repeated across shelves", titles.all { seen.add(it.preview.id) })
                                assertTrue(titles.all { primaryServerGenre(it, library.kind) == canonicalServerGenre(genre.name) })
                                put(genre.name, titles.size)
                            }
                        })
                    }
                })
            }
            System.getenv("NUVIO_EMBY_TEST_REPORT")?.let { File(it).writeText(report.toString()) }
        } finally {
            provider.signOut(session)
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
        }
    }
}

