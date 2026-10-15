package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.data.remote.api.TmdbFindResponse
import com.nuvio.tv.data.remote.api.TmdbFindResult
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbImage
import com.nuvio.tv.data.remote.api.TmdbImagesResponse
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class ServerPosterServiceTest {
    private val api = mockk<TmdbApi>()
    private fun item() = MetaPreview(
        id = "server:c1:movie", type = ContentType.MOVIE, name = "Polski tytuł",
        poster = "https://emby.example/poster", posterShape = PosterShape.POSTER,
        background = null, logo = null, description = "Opis z Emby", releaseInfo = "2020",
        imdbRating = 7.5f, genres = listOf("Akcja"), imdbId = "tt1234567"
    )

    @Test
    fun changesOnlyArtworkAndKeepsEmbyFallbackAndIdentity() = runBlocking {
        coEvery { api.findByExternalId("tt1234567", "test-key", "imdb_id") } returns Response.success(
            TmdbFindResponse(movieResults = listOf(TmdbFindResult(123)))
        )
        coEvery { api.getMovieImages(123, "test-key", "pl") } returns Response.success(
            TmdbImagesResponse(posters = listOf(TmdbImage("/en.jpg", "en"), TmdbImage("/pl.jpg", "pl")))
        )
        val service = ServerPosterService(api)
        val original = item()
        val result = service.enrich(listOf(original), "test-key").single()
        assertEquals(original.copy(poster = "https://image.tmdb.org/t/p/w500/pl.jpg", rawPosterUrl = original.poster), result)
        service.enrich(listOf(original), "test-key")
        coVerify(exactly = 1) { api.getMovieImages(123, "test-key", "pl") }
    }

    @Test
    fun missingPolishPosterOrNetworkFailurePreservesEmbyArtwork() = runBlocking {
        coEvery { api.findByExternalId("tt1234567", "test-key", "imdb_id") } returns Response.success(
            TmdbFindResponse(movieResults = listOf(TmdbFindResult(123)))
        )
        coEvery { api.getMovieImages(123, "test-key", "pl") } throws java.io.IOException()
        val service = ServerPosterService(api)
        assertEquals(listOf(item()), service.enrich(listOf(item()), "test-key"))
        coEvery { api.getMovieImages(123, "test-key", "pl") } returns Response.success(
            TmdbImagesResponse(posters = listOf(TmdbImage("/en.jpg", "en")))
        )
        assertEquals(listOf(item()), service.enrich(listOf(item()), "test-key"))
    }
}
