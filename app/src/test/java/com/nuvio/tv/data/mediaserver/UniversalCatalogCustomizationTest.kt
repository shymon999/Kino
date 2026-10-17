package com.nuvio.tv.data.mediaserver

import android.content.Context
import com.nuvio.tv.data.local.KinoPreferences
import com.nuvio.tv.data.local.KinoPreferencesDataStore
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UniversalCatalogCustomizationTest {
    @Test fun allGenresAreInitiallyVisibleAndHidingIsReversibleWithoutChangingSearchLibraries() = runTest {
        val library = FakeServerProvider.SERIES_LIBRARY.copy(genres = listOf(
            ServerGenre("anime", "Anime"), ServerGenre("kids", "Kids"),
            ServerGenre("sport", "Sport"), ServerGenre("war", "War"), ServerGenre("action", "Action")
        ))
        val anime = library.copy(id = "anime-library", name = "Anime")
        val (repository, connection) = fakeServerRepository(FakeServerProvider(), libraries = listOf(library, anime))
        val state = MutableStateFlow(KinoPreferences())
        val preferences = mockk<KinoPreferencesDataStore> {
            every { familyBuild } returns false
            every { this@mockk.state } returns state
        }
        val context = mockk<Context> { every { getString(any()) } returns "Genre" }
        val addons = mockk<AddonRepository> { every { getInstalledAddons() } returns flowOf(emptyList()) }
        val catalog = ServerCatalog(context, repository, addons, kinoPreferences = preferences)
        val initial = catalog.addons.first().single().catalogs
        assertTrue(initial.any { it.id == anime.id })
        for (genre in library.genres) assertTrue(initial.any {
            it.id == ServerCatalog.genreCatalogId(library.id, genre.id)
        })
        state.value = KinoPreferences(hiddenGenres = setOf("anime", "children"))
        val hidden = catalog.addons.first().single().catalogs
        assertFalse(hidden.any { it.id == anime.id })
        assertFalse(hidden.any { it.id == ServerCatalog.genreCatalogId(library.id, "anime") })
        assertTrue(hidden.any { it.id == ServerCatalog.genreCatalogId(library.id, "sport") })
        assertEquals(2, repository.connection(connection.id)!!.selectedLibraries.size)
        assertTrue(catalog.searchAddons.first().single().catalogs.any { it.id == "search.series" })
        state.value = KinoPreferences()
        assertEquals(initial, catalog.addons.first().single().catalogs)
    }
}
