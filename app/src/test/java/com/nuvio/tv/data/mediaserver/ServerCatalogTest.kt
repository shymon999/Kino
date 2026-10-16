package com.nuvio.tv.data.mediaserver

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.TrackingExternalIds
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.supportsExtra
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCatalogTest {
    @Test
    fun suspenseAliasesMergeAcrossLibrariesAndResolveOldLinks() = runTest {
        val first = FakeServerProvider.SERIES_LIBRARY.copy(genres = listOf(
            ServerGenre("c1", "Children"), ServerGenre("t1", "Suspense")
        ))
        val second = first.copy(id = "other", genres = listOf(
            ServerGenre("c2", "Kids"), ServerGenre("t2", "Thriller")
        ))
        val provider = FakeServerProvider()
        val (catalog, _, connection) = catalog(provider, listOf(first, second))
        val rows = catalog.addons.first().single().catalogs.filter { it.id.startsWith("genre:") }
        assertEquals(1, rows.size) // Children aliases are intentionally search-only.
        catalog.row(connection.id, ServerCatalog.genreCatalogId(second.id, "t2"))
        assertEquals(listOf(first.id to "t1", second.id to "t2"), provider.genreRequests)
    }

    @Test
    fun childrenAndAnimeRowsAreHiddenWithoutRemovingLibrariesFromSearch() = runTest {
        val movies = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(
            ServerGenre("mk", "Kids"), ServerGenre("ma", "Anime"), ServerGenre("mc", "Comedy")
        ))
        val series = FakeServerProvider.SERIES_LIBRARY.copy(genres = listOf(
            ServerGenre("sk", "Dla dzieci"), ServerGenre("sc", "Comedy")
        ))
        val anime = series.copy(id = "anime", name = "Anime", genres = listOf(ServerGenre("aa", "Anime")))
        val kids = series.copy(id = "kids", name = "Bajki", genres = listOf(ServerGenre("kc", "Children")))
        val animeMovies = movies.copy(id = "anime-movies", name = "Anime")
        val libraries = listOf(movies, series, anime, kids, animeMovies)
        val provider = FakeServerProvider()
        val (catalog, repository, connection) = catalog(provider, libraries)
        val rows = catalog.addons.first().single().catalogs
        assertFalse(rows.any { it.id in setOf(anime.id, kids.id, animeMovies.id) })
        val hidden = setOf("mk", "ma", "sk", "aa", "kc")
        assertFalse(rows.any { row -> libraries.any { library ->
            hidden.any { row.id == ServerCatalog.genreCatalogId(library.id, it) }
        } })
        assertTrue(rows.any { it.id == movies.id })
        assertTrue(rows.any { it.id == ServerCatalog.genreCatalogId(series.id, "sc") })
        assertEquals(libraries.map { it.id }, repository.connection(connection.id)!!.selectedLibraries.map { it.id })
        catalog.page(connection, "search.series", 0, mapOf("search" to "item"))
        catalog.page(connection, "search.movie", 0, mapOf("search" to "item"))
        assertEquals(listOf(
            ServerMediaKind.SERIES to listOf(series.id, anime.id, kids.id),
            ServerMediaKind.MOVIE to listOf(movies.id, animeMovies.id)
        ), provider.searches)
    }

    @Test
    fun mergedGenrePaginationUsesSourceOffsetsWhenLibrariesHaveDifferentSizes() = runTest {
        val small = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(ServerGenre("g1", "Action")))
        val large = small.copy(id = "large", genres = listOf(ServerGenre("g2", "Akcja")))
        val provider = FakeServerProvider().apply { libraryItemIds[small.id] = listOf("extra0", "extra1") }
        val (catalog, _, connection) = catalog(provider, listOf(small, large))
        val first = catalog.row(connection.id, ServerCatalog.genreCatalogId(small.id, "g1"))
        assertEquals(52, first.items.size)
        assertEquals(50, first.nextSkip)
        val second = catalog.page(first, first.nextSkip)
        val last = catalog.page(second, second.nextSkip)
        val ids = (first.items + second.items + last.items).map { it.id }
        assertEquals(122, ids.size)
        assertEquals(122, ids.toSet().size)
        assertFalse(last.hasMore)
    }

    @Test
    fun failedSourceDoesNotSilentlyProduceIncompleteMergedGenrePage() = runTest {
        val first = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(ServerGenre("g1", "Action")))
        val second = first.copy(id = "other", genres = listOf(ServerGenre("g2", "Akcja")))
        val provider = FakeServerProvider().apply { failingLibraries += second.id }
        val (catalog, _, connection) = catalog(provider, listOf(first, second))
        val error = runCatching { catalog.row(connection.id, ServerCatalog.genreCatalogId(first.id, "g1")) }.exceptionOrNull()
        assertEquals(ServerFailure.UNREACHABLE, error?.serverFailure())
    }

    @Test
    fun mergesSameGenreAcrossLibrariesAndKeepsMovieAndSeriesSeparate() = runTest {
        val series = FakeServerProvider.SERIES_LIBRARY.copy(genres = listOf(ServerGenre("g1", "Sci-Fi")))
        val anime = series.copy(id = "anime", name = "Anime", genres = listOf(ServerGenre("g2", "Sci-Fi & Fantasy")))
        val movies = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(ServerGenre("g3", "Science Fiction")))
        val provider = FakeServerProvider()
        val (catalog, repository, connection) = catalog(provider, listOf(series, anime, movies))
        val rows = catalog.addons.first().single().catalogs.filter { it.id.startsWith("genre:") }
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.apiType == "series" })
        val first = catalog.row(connection.id, ServerCatalog.genreCatalogId(anime.id, "g2"))
        assertEquals(50, first.items.size) // Fake libraries overlap completely.
        assertEquals(50, first.nextSkip)
        val next = catalog.page(first, first.nextSkip)
        assertEquals("50", ServerItemRef.parse(next.items.first().id)?.itemId)
        assertEquals(listOf(series.id to "g1", anime.id to "g2", series.id to "g1", anime.id to "g2"), provider.genreRequests)
        repository.setLibrarySelected(connection.id, anime.id, false)
        catalog.row(connection.id, ServerCatalog.genreCatalogId(series.id, "g1"))
        assertEquals(series.id to "g1", provider.genreRequests.last())
    }

    @Test
    fun sportStaysInLibraryFiltersWithoutClutteringAutomaticHomeRows() = runTest {
        val series = FakeServerProvider.SERIES_LIBRARY.copy(genres = listOf(ServerGenre("sport", "Sport")))
        val (catalog, _, _) = catalog(libraries = listOf(series))
        val rows = catalog.addons.first().single().catalogs
        assertFalse(rows.any { it.id.startsWith("genre:") })
        assertTrue(rows.single { it.id == series.id }.extra.single { it.name == "genre" }.options.orEmpty().contains("Sport"))
    }

    @Test
    fun exposesGenreFiltersAndSeparateHomeRowsWithoutDuplicatingDiscovery() = runTest {
        val movies = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(ServerGenre("g1", "Komedia")))
        val series = FakeServerProvider.SERIES_LIBRARY.copy(genres = listOf(ServerGenre("g1", "Komedia")))
        val (catalog, _, connection) = catalog(libraries = listOf(movies, series))
        val descriptors = catalog.addons.first().single().catalogs
        assertEquals(listOf("Komedia"), descriptors.single { it.id == movies.id }.extra.single { it.name == "genre" }.options)
        val movieRow = descriptors.single { it.id == ServerCatalog.genreCatalogId(movies.id, "g1") }
        val seriesRow = descriptors.single { it.id == ServerCatalog.genreCatalogId(series.id, "g1") }
        assertEquals("Movies · Komedia", movieRow.name)
        assertEquals("movie", movieRow.apiType)
        assertEquals("series", seriesRow.apiType)
        assertTrue(movieRow.showInHome)
        assertFalse(catalog.searchAddons.first().single().catalogs.any { it.id.startsWith("genre:") })
        assertEquals(connection.id, ServerCatalog.connectionId(catalog.addons.first().single().baseUrl))
    }

    @Test
    fun filtersAndPaginatesGeneratedGenreRowsUsingTheLibraryGenreId() = runTest {
        val movies = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(ServerGenre("g:1", "Science Fiction")))
        val provider = FakeServerProvider()
        val (catalog, _, connection) = catalog(provider, libraries = listOf(movies))
        val row = catalog.row(connection.id, ServerCatalog.genreCatalogId(movies.id, "g:1"))
        val next = catalog.page(row, row.nextSkip)
        assertEquals(listOf(movies.id to "g:1", movies.id to "g:1"), provider.genreRequests)
        assertEquals(mapOf("genre" to "Science Fiction"), next.extraArgs)
        assertEquals("50", ServerItemRef.parse(next.items.first().id)?.itemId)
        catalog.page(connection, movies.id, 0, mapOf("genre" to "Science Fiction"))
        assertEquals(movies.id to "g:1", provider.genreRequests.last())
    }

    @Test
    fun aliasesShareOneRowAndQueryAllGenreIdsWithoutChangingPagination() = runTest {
        val movies = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(
            ServerGenre("s0", "Sci-Fi & Fantasy"),
            ServerGenre("s1", "Sci-Fi"), ServerGenre("s2", "Science Fiction"),
            ServerGenre("x1", "Fantasy"),
            ServerGenre("f1", "Family"), ServerGenre("f2", "Familijny")
        ))
        val provider = FakeServerProvider()
        val (catalog, _, connection) = catalog(provider, libraries = listOf(movies))
        val rows = catalog.addons.first().single().catalogs.filter { it.id.startsWith("genre:") }
        assertEquals(3, rows.size)
        assertEquals("Movies · Science Fiction", rows.single { it.id == ServerCatalog.genreCatalogId(movies.id, "s0") }.name)
        val row = catalog.row(connection.id, ServerCatalog.genreCatalogId(movies.id, "s1"))
        catalog.page(row, row.nextSkip)
        assertEquals(listOf(movies.id to "s0,s1,s2", movies.id to "s0,s1,s2"), provider.genreRequests)
        catalog.page(connection, movies.id, 0, mapOf("genre" to "Familijny"))
        assertEquals(movies.id to "f1,f2", provider.genreRequests.last())
    }

    @Test
    fun discoversNewGenresOnRefreshAndThrottlesRepeatedVisits() = runTest {
        val provider = FakeServerProvider()
        val movies = FakeServerProvider.MOVIE_LIBRARY
        provider.genres[movies.id] = listOf(ServerGenre("g1", "Action"))
        val (catalog, _, _) = catalog(provider, libraries = listOf(movies))
        catalog.refreshLibrariesIfStale(0)
        provider.genres[movies.id] = listOf(ServerGenre("g1", "Action"), ServerGenre("g2", "Family"))
        catalog.refreshLibrariesIfStale(1)
        assertFalse(catalog.addons.first().single().catalogs.any { it.id == ServerCatalog.genreCatalogId(movies.id, "g2") })
        catalog.refreshLibrariesIfStale(900_001)
        assertEquals("Movies · Familijny", catalog.addons.first().single().catalogs.single {
            it.id == ServerCatalog.genreCatalogId(movies.id, "g2")
        }.name)
    }

    @Test
    fun missingGenresAndDeselectedLibrariesDoNotReturnUnfilteredTitles() = runTest {
        val movies = FakeServerProvider.MOVIE_LIBRARY.copy(genres = listOf(ServerGenre("g1", "Komedia")))
        val (catalog, repository, connection) = catalog(libraries = listOf(movies))
        val missing = runCatching { catalog.page(connection, movies.id, 0, mapOf("genre" to "Missing")) }.exceptionOrNull()
        assertEquals(ServerFailure.NOT_FOUND, missing?.serverFailure())
        repository.setLibrarySelected(connection.id, movies.id, false)
        val hidden = runCatching { catalog.page(connection, ServerCatalog.genreCatalogId(movies.id, "g1"), 0) }.exceptionOrNull()
        assertEquals(ServerFailure.NOT_FOUND, hidden?.serverFailure())
    }

    private val installedAddons = MutableStateFlow<List<Addon>>(emptyList())
    private val context = mockk<Context> {
        every { getString(any()) } returns "Continue watching"
        every { getString(R.string.genre_comedy) } returns "Komedia"
        every { getString(R.string.genre_science_fiction) } returns "Science Fiction"
        every { getString(R.string.genre_family) } returns "Familijny"
        every { getString(R.string.nav_movies) } returns "Movies"
        every { getString(R.string.nav_series) } returns "Series"
    }
    private val addonRepository = mockk<AddonRepository> {
        every { getInstalledAddons() } returns installedAddons
    }

    private fun catalog(
        provider: FakeServerProvider = FakeServerProvider(),
        libraries: List<ServerLibrary> = listOf(FakeServerProvider.MOVIE_LIBRARY, FakeServerProvider.SERIES_LIBRARY)
    ): Triple<ServerCatalog, ServerRepository, ServerConnection> {
        val (repository, connection) = fakeServerRepository(provider, libraries = libraries)
        return Triple(ServerCatalog(context, repository, addonRepository), repository, connection)
    }

    private suspend fun ServerCatalog.page(connection: ServerConnection, catalogId: String, skip: Int, extra: Map<String, String> = emptyMap()) =
        catalog(
            addonBaseUrl = ServerCatalog.baseUrl(connection.id),
            addonId = ServerCatalog.addonId(connection.id),
            addonName = "Fake · Box",
            catalogId = catalogId,
            catalogName = "Box · Movies",
            type = "movie",
            skip = skip,
            extraArgs = extra
        )

    @Test
    fun paginatesLibraryWithOpaqueNumericIds() = runTest {
        val (catalog, _, connection) = catalog()

        val first = catalog.page(connection, FakeServerProvider.MOVIE_LIBRARY.id, skip = 0)
        assertEquals(ServerCatalog.PAGE_SIZE, first.items.size)
        assertTrue(first.hasMore)
        assertEquals(ServerCatalog.PAGE_SIZE, first.nextSkip)
        assertEquals(ServerItemRef(connection.id, "0"), ServerItemRef.parse(first.items.first().id))

        val last = catalog.page(connection, FakeServerProvider.MOVIE_LIBRARY.id, skip = 100)
        assertEquals(20, last.items.size)
        assertFalse(last.hasMore)
    }

    @Test
    fun exposesSelectedLibrariesAsAttributedAddonRows() = runTest {
        val (catalog, repository, connection) = catalog()
        repository.setLibrarySelected(connection.id, FakeServerProvider.SERIES_LIBRARY.id, false)

        val addon = catalog.addons.first().single()
        assertEquals(ServerCatalog.addonId(connection.id), addon.id)
        assertEquals("Fake · Box", addon.displayName)
        assertTrue(ServerCatalog.isServerAddon(addon.baseUrl))
        assertEquals(listOf("resume", "10", "search.movie"), addon.catalogs.map { it.id })
        assertEquals("Movies", addon.catalogs.single { it.id == "10" }.name)
        assertEquals(listOf("10", "search.movie"), catalog.searchAddons.first().single().catalogs.map { it.id })
        assertEquals(listOf("search.movie"), addon.catalogs.filter { it.supportsExtra("search") }.map { it.id })
        assertTrue(addon.catalogs.last().extra.single().isRequired)
    }

    @Test
    fun catalogMetadataChangesTheLoadSignature() = runTest {
        val (catalog, repository, connection) = catalog()
        val native = catalog.addons.first().single().version

        repository.setCatalogMetadata(connection.id, true)

        assertTrue(native != catalog.addons.first().single().version)
    }

    @Test
    fun disabledConnectionsProvideNoRows() = runTest {
        val (catalog, repository, connection) = catalog()
        repository.setEnabled(connection.id, false)

        assertTrue(catalog.addons.first().isEmpty())
        assertTrue(catalog.libraries().isEmpty())
    }

    @Test
    fun searchesEachTypeOnceAndResumes() = runTest {
        val provider = FakeServerProvider()
        val (catalog, _, connection) = catalog(provider)

        val search = catalog.page(connection, "search.series", skip = 0, extra = mapOf("search" to "item"))
        assertEquals(listOf(ServerItemRef(connection.id, "7").encode()), search.items.map { it.id })
        assertFalse(search.hasMore)
        assertEquals(listOf(ServerMediaKind.SERIES to listOf(FakeServerProvider.SERIES_LIBRARY.id)), provider.searches)

        val resume = catalog.page(connection, "resume", skip = 0)
        assertEquals(listOf(ServerItemRef(connection.id, "3").encode()), resume.items.map { it.id })
    }

    @Test
    fun mixedLibrariesServeBothSearches() = runTest {
        val provider = FakeServerProvider()
        val mixed = ServerLibrary("90", "Everything", ServerMediaKind.MIXED)
        val (catalog, _, connection) = catalog(provider, libraries = listOf(mixed))

        val addon = catalog.addons.first().single()
        assertEquals(listOf("resume", "90", "search.movie", "search.series"), addon.catalogs.map { it.id })
        assertEquals(listOf("search.movie", "search.series"), catalog.searchAddons.first().single().catalogs.map { it.id })
        catalog.page(connection, "search.movie", skip = 0, extra = mapOf("search" to "item"))
        assertEquals(listOf(ServerMediaKind.MOVIE to listOf("90")), provider.searches)
    }

    @Test
    fun collectionCardsOpenTheirContentsAsACatalog() = runTest {
        val (catalog, _, connection) = catalog(
            libraries = listOf(FakeServerProvider.MOVIE_LIBRARY, FakeServerProvider.COLLECTION_LIBRARY)
        )
        assertTrue(catalog.titleLibraries().none { it.library.kind == ServerMediaKind.COLLECTION })
        assertTrue(catalog.searchAddons.first().single().catalogs.none { it.id == FakeServerProvider.COLLECTION_LIBRARY.id })

        val ref = ServerItemRef(connection.id, "c-popular")
        assertTrue(ServerCatalog.isCollection(ref.encode(), "collection"))
        assertFalse(ServerCatalog.isCollection(ref.encode(), "movie"))

        val row = catalog.collectionRow(ref)
        assertEquals("collection:c-popular", row.catalogId)
        assertEquals("Item c-popular", row.catalogName)
        assertEquals(listOf("movie", "series"), row.items.map { it.apiType })
        assertFalse(row.hasMore)
    }

    @Test
    fun catalogMetadataUsesCompatibleIdsOnly() = runTest {
        val provider = FakeServerProvider().apply {
            indexedIds["0"] = TrackingExternalIds(imdb = "tt0111161", tmdb = 278)
            indexedIds["1"] = TrackingExternalIds(tmdb = 550)
        }
        val (catalog, repository, connection) = catalog(provider)

        val native = catalog.page(connection, FakeServerProvider.MOVIE_LIBRARY.id, skip = 0).items.take(3)
        assertTrue(native.all { ServerItemRef.isServerId(it.id) })

        repository.setCatalogMetadata(connection.id, true)
        val mapped = catalog.page(connection, FakeServerProvider.MOVIE_LIBRARY.id, skip = 0).items.take(3)
        assertEquals(listOf("tmdb:278", "tmdb:550", ServerItemRef(connection.id, "2").encode()), mapped.map { it.id })
        assertEquals("tt0111161", mapped.first().imdbId)
        assertEquals("Item 0", mapped.first().name)
    }

    @Test
    fun catalogIdFollowsInstalledMetaAddonsInOrder() {
        val title = ServerTitle(
            preview(ServerItemRef("c1", "7").encode(), ContentType.SERIES),
            TrackingExternalIds(imdb = "tt1", tmdb = 2, kitsu = 3)
        )
        val kitsu = metaAddon("kitsu", listOf("kitsu:"))
        val cinemeta = metaAddon("cinemeta", listOf("tt"))
        assertEquals("kitsu:3", title.catalogPreview(listOf(kitsu, cinemeta)).id)
        assertEquals("tt1", title.catalogPreview(listOf(cinemeta, kitsu)).id)
        assertEquals("tmdb:2", title.catalogPreview(listOf(metaAddon("mal", listOf("mal:")))).id)
        assertEquals(title.preview.id, title.copy(externalIds = TrackingExternalIds(kitsu = 3)).catalogPreview(emptyList()).id)
    }

    @Test
    fun collectionsKeepServerIdentity() {
        val collection = ServerTitle(
            preview(ServerItemRef("c1", "b1").encode(), ContentType.UNKNOWN, rawType = "collection"),
            TrackingExternalIds(imdb = "tt1")
        )
        assertEquals(collection.preview, collection.catalogPreview(listOf(metaAddon("any", emptyList()))))
    }

    @Test
    fun opensAnyServerRowByCatalogId() = runTest {
        val (catalog, _, connection) = catalog()

        val library = catalog.row(connection.id, FakeServerProvider.MOVIE_LIBRARY.id)
        assertEquals("Movies", library.catalogName)
        assertEquals(ServerCatalog.PAGE_SIZE, library.items.size)
        assertTrue(library.hasMore)

        val resume = catalog.row(connection.id, "resume")
        assertEquals("Continue watching", resume.catalogName)
        assertEquals(1, resume.items.size)

        val missing = runCatching { catalog.row(connection.id, "unknown") }.exceptionOrNull()
        assertEquals(ServerFailure.NOT_FOUND, missing?.serverFailure())
    }

    @Test
    fun loadsNativeDetails() = runTest {
        val (catalog, _, connection) = catalog()
        val details = catalog.details(ServerItemRef(connection.id, FakeServerProvider.SHOW_ID))
        assertEquals("series", details.meta.apiType)
        assertFalse(details.externalIds.hasAny)
        assertEquals(ServerItemRef(connection.id, FakeServerProvider.EPISODE_ID), ServerItemRef.parse(details.meta.videos.single().id))
    }

    @Test
    fun unknownConnectionsFailAsNotFound() = runTest {
        val (catalog, _, _) = catalog()
        val failure = runCatching {
            catalog.catalog(ServerCatalog.baseUrl("missing"), "server.missing", "", "10", "", "movie", 0, emptyMap())
        }.exceptionOrNull()
        assertEquals(ServerFailure.NOT_FOUND, failure?.serverFailure())
        assertNull(ServerCatalog.connectionId("https://addon.example"))
    }

    private fun preview(id: String, type: ContentType, rawType: String = type.toApiString()) = MetaPreview(
        id = id,
        type = type,
        rawType = rawType,
        name = "Show",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList()
    )

    private fun metaAddon(id: String, prefixes: List<String>) = Addon(
        id = id,
        name = id,
        version = "1",
        description = null,
        logo = null,
        baseUrl = "https://$id.example",
        catalogs = emptyList(),
        types = listOf(ContentType.MOVIE, ContentType.SERIES),
        resources = listOf(AddonResource(name = "meta", types = listOf("movie", "series"), idPrefixes = prefixes)),
        idPrefixes = prefixes
    )
}
