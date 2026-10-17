package com.nuvio.tv.data.mediaserver

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.R
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCatalogLocaleTest {
    @Test
    fun polishAppUsesPolishRowsAndGenreFiltersOnEnglishDevice() = runTest {
        val previousTag = LocaleCache.localeTag
        try {
            val polish = mockk<Context> {
                every { getString(any()) } answers {
                    when (firstArg<Int>()) {
                        R.string.genre_action -> "Akcja"
                        R.string.genre_comedy -> "Komedia"
                        R.string.servers_resume_row -> "Kontynuuj oglądanie"
                        else -> "Polski nagłówek"
                    }
                }
            }
            val resources = mockk<Resources> {
                every { configuration } returns Configuration()
            }
            val english = mockk<Context> {
                every { this@mockk.resources } returns resources
                every { createConfigurationContext(any()) } returns polish
                every { getString(any()) } answers {
                    when (firstArg<Int>()) {
                        R.string.genre_action -> "Action"
                        R.string.genre_comedy -> "Comedy"
                        else -> "English heading"
                    }
                }
            }
            val library = FakeServerProvider.SERIES_LIBRARY.copy(genres = listOf(
                ServerGenre("action", "Action"), ServerGenre("comedy", "Comedy")
            ))
            val (repository, _) = fakeServerRepository(FakeServerProvider(), libraries = listOf(library))
            val addons = mockk<AddonRepository> {
                every { getInstalledAddons() } returns flowOf(emptyList())
            }
            val catalog = ServerCatalog(english, repository, addons)

            LocaleCache.localeTag = "en-US"
            every { english.createConfigurationContext(any()) } returns english
            val englishRows = catalog.addons.first().single().catalogs
            assertTrue(englishRows.any { it.name.endsWith(" · Action") })

            LocaleCache.localeTag = "pl"
            every { english.createConfigurationContext(any()) } returns polish
            val polishRows = catalog.addons.first().single().catalogs
            assertTrue(polishRows.any { it.name.endsWith(" · Akcja") })
            assertTrue(polishRows.any { it.name.endsWith(" · Komedia") })
            val filters = polishRows.single { it.id == library.id }.extra
                .single { it.name == "genre" }.options
            assertEquals(listOf("Akcja", "Komedia"), filters)
            verify { polish.getString(R.string.genre_action) }
            verify { polish.getString(R.string.genre_comedy) }
        } finally {
            LocaleCache.localeTag = previousTag
        }
    }
}
