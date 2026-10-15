package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.legacyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCategoryRowsTest {
    private fun row(type: String, id: String = "genre:lib:g1", server: Boolean = true) = CatalogRow(
        addonId = if (server) "server.c1" else "external",
        addonName = "VOG",
        addonBaseUrl = if (server) "nuvio-server://c1" else "https://external.example",
        catalogId = id, catalogName = "Filmy · Akcja", type = ContentType.fromString(type),
        rawType = type, items = emptyList(), extraArgs = mapOf("genre" to "Akcja")
    )

    @Test
    fun categoriesKeepOnlyTheSelectedServerTypeInTheExistingOrder() {
        val movies = row("movie")
        val otherMovies = row("movie", "genre:lib:g2").copy(catalogName = "Filmy · Komedia", extraArgs = mapOf("genre" to "Comedy"))
        val rows = listOf(otherMovies, row("series"), movies, row("movie", "resume"), row("movie", server = false))
        val selected = serverCategoryRows(rows.map(HomeRow::Catalog), "movie").filterIsInstance<HomeRow.Catalog>()
        assertEquals(listOf("Komedia", "Akcja"), selected.map { it.row.catalogName })
        assertEquals(listOf(otherMovies.legacyKey(), movies.legacyKey()), selected.map { it.row.legacyKey() })
        assertEquals(1, serverCategoryRows(rows.map(HomeRow::Catalog), "series").size)
    }

    @Test
    fun unloadedRowsKeepTheirRequestKeyAndHaveFocusableLoadingCards() {
        val loading = HomeRow.PlaceholderCatalog(
            catalogKey = "server.c1_movie_genre:lib:g1", stableCatalogKey = "stable",
            addonId = "server.c1", addonName = "VOG", addonBaseUrl = "nuvio-server://c1",
            catalogId = "genre:lib:g1", catalogName = "Filmy · Akcja", apiType = "movie", displayTitle = "Filmy · Akcja - Film"
        )
        val selected = (serverCategoryRows(listOf(loading), "movie").single() as HomeRow.Catalog).row
        assertEquals(loading.catalogKey, selected.legacyKey())
        assertEquals("Akcja", selected.catalogName)
        assertTrue(selected.isLoading)
        assertFalse(selected.hasMore)
        assertTrue(selected.items.isNotEmpty())
        assertTrue(selected.items.all { it.id.startsWith("__placeholder_") })
        assertTrue(serverCategoryRows(listOf(loading), "series").isEmpty())
    }

    @Test
    fun serverHomeHeadersNeverRepeatTheMediaType() {
        assertEquals("Filmy · Akcja", catalogRowTitle(row("movie"), true, "Film", "Serial"))
        assertEquals("Filmy · Akcja - Film", catalogRowTitle(row("movie", server = false), true, "Film", "Serial"))
    }
}
