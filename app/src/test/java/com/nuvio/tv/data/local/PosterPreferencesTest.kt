package com.nuvio.tv.data.local

import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class PosterPreferencesTest {
    private val key = "a".repeat(32)
    private var record: String? = null
    private val storage = mockk<ProtectedProfilePreferences> {
        every { read(0) } answers { record }
        every { write(0, any()) } answers { record = secondArg(); Unit }
    }

    @Test fun universalStartsWithEmbyAndNoBundledCredentials() {
        val store = PosterPreferencesDataStore(storage, false, key)
        assertFalse(store.state.value.polishPosters)
        assertFalse(store.state.value.hasApiKey)
        assertEquals("", store.activeApiKey())
    }

    @Test fun saveDisableReenableAndRemoveSurviveRestart() {
        val store = PosterPreferencesDataStore(storage)
        store.save(true, " $key ")
        val reopened = PosterPreferencesDataStore(storage)
        assertEquals(key, reopened.activeApiKey())
        assertFalse(reopened.state.value.toString().contains(key))
        reopened.save(false, key)
        assertEquals("", PosterPreferencesDataStore(storage).activeApiKey())
        reopened.save(true, reopened.savedApiKey())
        assertEquals(key, reopened.activeApiKey())
        reopened.save(false, "")
        val removed = PosterPreferencesDataStore(storage)
        assertFalse(removed.state.value.hasApiKey)
        assertEquals("", removed.savedApiKey())
    }

    @Test fun familyRetainsBundledKeyButRemovalDoesNotRestoreIt() {
        val store = PosterPreferencesDataStore(storage, true, key)
        assertEquals(key, store.activeApiKey())
        store.save(false, "")
        assertEquals("", PosterPreferencesDataStore(storage, true, key).activeApiKey())
    }

    @Test fun rejectedKeyAndEmptyEnabledKeyDoNotChangeSettings() {
        val store = PosterPreferencesDataStore(storage)
        assertThrows(IllegalArgumentException::class.java) { store.save(true, "") }
        assertThrows(IllegalArgumentException::class.java) { store.save(true, "read-access-token") }
        assertNull(record)
        assertFalse(store.state.value.polishPosters)
    }

    @Test fun failedProtectedWriteKeepsTheActiveConfiguration() {
        val store = PosterPreferencesDataStore(storage)
        store.save(true, key)
        every { storage.write(0, any()) } throws java.io.IOException("write failed")
        assertThrows(java.io.IOException::class.java) { store.save(false, "") }
        assertEquals(key, store.activeApiKey())
        assertTrue(store.state.value.polishPosters)
    }

    @Test fun replacingAConfiguredKeyStillEmitsAnUpdate() {
        val store = PosterPreferencesDataStore(storage)
        store.save(true, key)
        val previous = store.state.value
        store.save(true, "b".repeat(32))
        assertNotEquals(previous, store.state.value)
    }
}
