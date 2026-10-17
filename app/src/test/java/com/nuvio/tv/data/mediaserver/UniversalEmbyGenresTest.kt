package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.data.mediaserver.emby.EmbyProvider
import com.nuvio.tv.data.mediaserver.mediabrowser.ServerClientIdentity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class UniversalEmbyGenresTest {
    @Test fun universalReturnsServerGenresWithoutPersonalAssignmentsOrMinimumShelfSize() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requestedPaths += chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"Items":[{"Id":"anime","Name":"Anime"},{"Id":"kids","Name":"Kids"},{"Id":"sport","Name":"Sport"}],"TotalRecordCount":3}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        val provider = EmbyProvider(http, ServerClientIdentity("Test TV", "test", { "test-device" }), familyCuration = false)
        val connection = ServerConnection("c", "emby", "Test", "https://emby.example", "s", "u", "user", "credential")
        val genres = provider.libraryGenres(ServerSession(connection, "test-token"),
            ServerLibrary("series", "Series", ServerMediaKind.SERIES))
        assertEquals(listOf("Anime", "Kids", "Sport"), genres.map { it.name })
        assertEquals(listOf("/emby/Genres"), requestedPaths)
    }
}
