package com.nuvio.tv.data.local

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class KinoPreferencesTest {
    private class Storage {
        val values = mutableMapOf<String, Any>()
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        init {
            every { prefs.getInt(any(), any()) } answers { values[firstArg<String>()] as? Int ?: secondArg() }
            every { prefs.getBoolean(any(), any()) } answers { values[firstArg<String>()] as? Boolean ?: secondArg() }
            every { prefs.getStringSet(any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                ((values[firstArg<String>()] as? Set<String>) ?: secondArg<Set<String>>()).toMutableSet()
            }
            justRun { prefs.registerOnSharedPreferenceChangeListener(any()) }
            every { prefs.edit() } returns editor
            every { editor.putInt(any(), any()) } answers { values[firstArg()] = secondArg<Int>(); editor }
            every { editor.putBoolean(any(), any()) } answers { values[firstArg()] = secondArg<Boolean>(); editor }
            every { editor.putStringSet(any(), any()) } answers { values[firstArg()] = secondArg<Set<String>>().toSet(); editor }
            justRun { editor.apply() }
        }
    }

    @Test fun universalStartsUnlimitedAndDoesNotHideGenres() {
        val store = KinoPreferencesDataStore(Storage().prefs, false)
        assertNull(store.state.value.bitrateBps)
        assertTrue(store.state.value.hiddenGenres.isEmpty())
        assertFalse(store.state.value.serverSetupComplete)
    }

    @Test fun productionConstructorUsesTheCorrectDistributionDefault() {
        val storage = Storage()
        val context = mockk<android.content.Context> {
            every { getSharedPreferences("kino_preferences", android.content.Context.MODE_PRIVATE) } returns storage.prefs
        }
        val settings = KinoPreferencesDataStore(context).state.value
        if (com.nuvio.tv.BuildConfig.FAMILY_BUILD) assertEquals(40_000_000L, settings.bitrateBps)
        else assertNull(settings.bitrateBps)
    }

    @Test fun familyRetainsFortyMbpsDefault() {
        assertEquals(40_000_000L, KinoPreferencesDataStore(Storage().prefs, true).state.value.bitrateBps)
    }

    @Test fun bitrateAndHiddenGenresSurviveStoreRecreationAndCanBeRestored() {
        val storage = Storage()
        val first = KinoPreferencesDataStore(storage.prefs, false)
        first.setBitrateMbps(20)
        first.setHiddenGenres(setOf("anime", "children"))
        first.completeServerSetup()
        val reopened = KinoPreferencesDataStore(storage.prefs, false)
        assertEquals(20_000_000L, reopened.state.value.bitrateBps)
        assertEquals(setOf("anime", "children"), reopened.state.value.hiddenGenres)
        assertTrue(reopened.state.value.serverSetupComplete)
        reopened.setBitrateMbps(null)
        reopened.setHiddenGenres(emptySet())
        val final = KinoPreferencesDataStore(storage.prefs, false)
        assertNull(final.state.value.bitrateBps)
        assertTrue(final.state.value.hiddenGenres.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBitrateCannotBeSaved() {
        KinoPreferencesDataStore(Storage().prefs, false).setBitrateMbps(-5)
    }
}
