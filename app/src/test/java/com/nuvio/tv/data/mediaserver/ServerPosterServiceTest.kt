package com.nuvio.tv.data.mediaserver

import android.content.Context
import com.nuvio.tv.data.remote.api.TmdbFindResponse
import com.nuvio.tv.data.remote.api.TmdbFindResult
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbImage
import com.nuvio.tv.data.remote.api.TmdbImagesResponse
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ServerPosterServiceTest {
    @get:org.junit.Rule val folder = org.junit.rules.TemporaryFolder()

    @Test fun restartRestoresPolishPosterWithoutRepeatingTmdbLookup() = runTest {
        match()
        coEvery { api.getMovieImages(123, "test-key", "pl") } returns Response.success(
            TmdbImagesResponse(posters = listOf(TmdbImage("/pl.jpg", "pl"))))
        val disk = BoundedMetadataDiskCache(folder.newFolder())
        val first = ServerPosterService(api, backgroundScope, "test-key", disk)
        first.cachedAndRequest(listOf(item()))
        runCurrent()
        val restarted = ServerPosterService(api, backgroundScope, "test-key", disk)
        restarted.cachedAndRequest(listOf(item()))
        runCurrent()
        assertEquals("https://image.tmdb.org/t/p/w500/pl.jpg", restarted.cached(listOf(item())).single().poster)
        coVerify(exactly = 1) { api.findByExternalId(any(), any(), any()) }
    }

    private val api = mockk<TmdbApi>()
    private fun item() = MetaPreview(
        id = "server:c1:movie", type = ContentType.MOVIE, name = "Polski tytul",
        poster = "https://emby.example/poster", posterShape = PosterShape.POSTER,
        background = null, logo = null, description = "Opis z Emby", releaseInfo = "2020",
        imdbRating = 7.5f, genres = listOf("Akcja"), imdbId = "tt1234567"
    )

    private fun match() {
        coEvery { api.findByExternalId("tt1234567", "test-key", "imdb_id") } returns Response.success(
            TmdbFindResponse(movieResults = listOf(TmdbFindResult(123)))
        )
    }

    @Test
    fun returnsEmbyImmediatelyWhilePolishArtworkIsStillLoadingAndCoalescesDuplicates() = runTest {
        match()
        val release = CompletableDeferred<Unit>()
        coEvery { api.getMovieImages(123, "test-key", "pl") } coAnswers {
            release.await()
            Response.success(TmdbImagesResponse(posters = listOf(TmdbImage("/en.jpg", "en"), TmdbImage("/pl.jpg", "pl"))))
        }
        val service = ServerPosterService(api, backgroundScope, "test-key")
        val original = item()
        assertEquals(listOf(original, original), service.cachedAndRequest(listOf(original, original)))
        service.cachedAndRequest(listOf(original))
        runCurrent()
        assertEquals(listOf(original), service.cached(listOf(original)))
        release.complete(Unit)
        runCurrent()
        assertEquals(original.copy(poster = "https://image.tmdb.org/t/p/w500/pl.jpg", rawPosterUrl = original.poster),
            service.cached(listOf(original)).single())
        service.cachedAndRequest(listOf(original))
        runCurrent()
        coVerify(exactly = 1) { api.getMovieImages(123, "test-key", "pl") }
    }

    @Test
    fun missingPolishPosterOrNetworkFailurePreservesEmbyAndCanRetryTransientFailure() = runTest {
        match()
        coEvery { api.getMovieImages(123, "test-key", "pl") } throws java.io.IOException()
        val service = ServerPosterService(api, backgroundScope, "test-key")
        service.cachedAndRequest(listOf(item()))
        runCurrent()
        assertEquals(listOf(item()), service.cached(listOf(item())))
        coEvery { api.getMovieImages(123, "test-key", "pl") } returns Response.success(
            TmdbImagesResponse(posters = listOf(TmdbImage("/en.jpg", "en")))
        )
        service.cachedAndRequest(listOf(item()))
        runCurrent()
        service.cachedAndRequest(listOf(item()))
        runCurrent()
        assertEquals(listOf(item()), service.cached(listOf(item())))
        coVerify(exactly = 2) { api.getMovieImages(123, "test-key", "pl") }
    }

    @Test
    fun artworkUpdatesDoNotAdvanceOrDuplicateCatalogPages() = runTest {
        match()
        coEvery { api.getMovieImages(123, "test-key", "pl") } returns Response.success(
            TmdbImagesResponse(posters = listOf(TmdbImage("/pl.jpg", "pl")))
        )
        val service = ServerPosterService(api, backgroundScope, "test-key")
        val catalog = ServerCatalog(mockk<Context>(), fakeServerRepository().first, mockk(), service)
        val row = CatalogRow("server.c1", "Emby", "nuvio-server://c1", "genre:1:2", "Akcja",
            ContentType.MOVIE, items = listOf(item()), currentPage = 2, nextSkip = 150,
            hasMore = true, supportsSkip = true, skipStep = 50)
        service.cachedAndRequest(row.items)
        runCurrent()
        assertEquals(row.copy(items = listOf(item().copy(poster = "https://image.tmdb.org/t/p/w500/pl.jpg",
            rawPosterUrl = item().poster))), catalog.cachedPosters(row))
    }

    @Test
    fun noApiKeyNeverStartsExternalRequests() = runTest {
        val service = ServerPosterService(api, backgroundScope, "")
        assertEquals(listOf(item()), service.cachedAndRequest(listOf(item())))
        runCurrent()
        coVerify(exactly = 0) { api.findByExternalId(any(), any(), any()) }
    }
}
