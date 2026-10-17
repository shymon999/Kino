package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.data.mediaserver.emby.EmbyProvider
import com.nuvio.tv.data.mediaserver.mediabrowser.ServerClientIdentity
import java.io.DataInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FamilyEmbyLiveCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun actualLibrarySurvivesProviderRestartWithoutNetworkAndWithinBudget() = runBlocking<Unit> {
        val path = System.getenv("NUVIO_EMBY_TEST_CONFIG")
        assumeNotNull(path)
        val configuration = File(path!!).readText()
        fun field(name: String): String {
            val literal = Regex("String\\s+$name\\s*=\\s*(\"(?:\\\\.|[^\"\\\\])*\");")
                .find(configuration)?.groupValues?.get(1) ?: error("Missing test configuration field")
            return Json.parseToJsonElement(literal).jsonPrimitive.content
        }
        val calls = AtomicInteger()
        val searches = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            if (chain.request().url.queryParameter("searchTerm") != null) searches.incrementAndGet()
            chain.proceed(chain.request())
        }.build()
        val directory = folder.newFolder()
        val cache = BoundedMetadataDiskCache(directory)
        val device = "kino-cache-test-${UUID.randomUUID()}"
        val identity = ServerClientIdentity("Family cache integration test", "1.0") { device }
        val first = EmbyProvider(http, identity, cache)
        val login = first.signIn(field("FAMILY_EMBY_URL"), field("FAMILY_EMBY_USERNAME"), field("FAMILY_EMBY_PASSWORD"))
        val draft = ServerConnection("cache-test", "emby", "Cache test", login.address, login.serverId, login.userId,
            login.userName, "temporary-cache-test")
        var session = ServerSession(draft, login.token)
        try {
            val libraries = first.libraries(session).filter { it.name == "Filmy" || it.name == "Seriale" }
            session = ServerSession(draft.copy(libraries = libraries), login.token)
            val movies = libraries.filter { it.kind == ServerMediaKind.MOVIE }
            val started = System.nanoTime()
            val results = first.search(session, ServerMediaKind.MOVIE, movies, "nold schwarezenegger", 100)
            val initialMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { "Arnold Schwarzenegger" in it.peopleNames })
            assertEquals(0, searches.get())

            val restarted = EmbyProvider(http, identity, BoundedMetadataDiskCache(directory))
            val before = calls.get()
            val diskStarted = System.nanoTime()
            val saved = restarted.search(session, ServerMediaKind.MOVIE, movies, "nold schwarezenegger", 100)
            val diskMs = (System.nanoTime() - diskStarted) / 1_000_000
            assertEquals(results, saved)
            assertEquals("Restarted search unexpectedly called the server", before, calls.get())

            val item = ServerItemRef.parse(results.first().preview.id)!!
            val details = first.details(session, item.itemId)
            val personId = ServerItemRef.parse(details.meta.castMembers.first { it.name == "Arnold Schwarzenegger" }.serverPersonId)!!.itemId
            val credits = first.personDetails(session, personId)
            val detailCalls = calls.get()
            assertEquals(details.copy(userStates = emptyList()), restarted.details(session, item.itemId))
            assertEquals(credits, restarted.personDetails(session, personId))
            assertEquals("Saved details or credits unexpectedly called the server", detailCalls, calls.get())
            assertTrue(cache.sizeBytes() <= 64L * 1024 * 1024)
            directory.listFiles().orEmpty().forEach { file ->
                DataInputStream(file.inputStream().buffered()).use { input ->
                    input.readLong()
                    val text = GZIPInputStream(input).bufferedReader().readText()
                    assertFalse("Authentication token persisted in metadata", text.contains(login.token))
                }
            }
            System.getenv("NUVIO_FAMILY_CACHE_REPORT")?.let { report ->
                File(report).writeText(buildJsonObject {
                    put("actor_results", results.size)
                    put("first_index_search_ms", initialMs)
                    put("restart_disk_search_ms", diskMs)
                    put("restart_search_network_requests", 0)
                    put("cached_details_network_requests", 0)
                    put("metadata_bytes", cache.sizeBytes())
                    put("metadata_limit_bytes", 64L * 1024 * 1024)
                    put("token_persisted", false)
                }.toString())
            }
        } finally {
            first.signOut(session)
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
        }
    }
}
