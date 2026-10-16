package com.nuvio.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.UserProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyAddonDefaultsTest {
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private fun create(initial: Preferences = emptyPreferences()): AddonPreferences {
        val store = MemoryStore(initial)
        val factory = mockk<ProfileDataStoreFactory> { every { get(1, any()) } returns store }
        val profile = UserProfile(1, "Viewer", "#000000")
        val profiles = mockk<ProfileManager> {
            every { activeProfileId } returns MutableStateFlow(1)
            every { this@mockk.profiles } returns MutableStateFlow(listOf(profile))
            every { activeProfile } returns profile
        }
        return AddonPreferences(factory, profiles)
    }

    @Test fun freshInstallDoesNotFetchExternalDefaultAddonsButAcceptsExplicitInstallation() = runTest {
        val preferences = create()
        preferences.ensureMigrated()
        assertTrue(preferences.installedAddonUrls.first().isEmpty())
        assertTrue(preferences.addAddon("https://chosen.example/manifest.json"))
        assertEquals(listOf("https://chosen.example"), preferences.installedAddonUrls.first())
    }

    @Test fun migrationKeepsExistingUserSelectedAddons() = runTest {
        val preferences = create(mutablePreferencesOf(
            stringSetPreferencesKey("installed_addon_urls") to setOf("https://chosen.example")
        ))
        preferences.ensureMigrated()
        assertEquals(listOf("https://chosen.example"), preferences.installedAddonUrls.first())
    }
}
