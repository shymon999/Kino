@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.data.local.*
import com.nuvio.tv.data.mediaserver.*
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.HomeLayout
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class UniversalEmbySetupTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private class Harness(existing: Boolean = false, selected: Boolean = true) {
        val connection = ServerConnection("c", "emby", "Server", "https://emby.example", "s", "u", "user", "credential",
            listOf(ServerLibrary("movies", "Movies", ServerMediaKind.MOVIE, selected)))
        var connections = if (existing) listOf(connection) else emptyList()
        val repository = mockk<ServerRepository> {
            every { ensureLoaded() } just Runs
            every { enabledConnections() } answers { connections }
        }
        val preferences = mockk<KinoPreferencesDataStore>(relaxed = true)
        val layout = mockk<LayoutPreferenceDataStore>(relaxed = true) {
            every { hasChosenLayout } returns flowOf(existing)
        }
        val experience = mockk<ExperienceModeDataStore>(relaxed = true) {
            every { mode } returns flowOf(if (existing) ExperienceMode.ADVANCED else null)
        }
        val onboarding = mockk<AppOnboardingDataStore>(relaxed = true)
        val player = mockk<PlayerSettingsDataStore>(relaxed = true)
        val trailers = mockk<TrailerSettingsDataStore>(relaxed = true)
        val viewModel = UniversalEmbySetupViewModel(repository, preferences, layout, experience, onboarding, player, trailers)
    }

    @Test fun freshEmbyLoginCompletesWithoutNuvioAccountAndAppliesInitialLayout() = runTest(dispatcher) {
        val h = Harness()
        h.connections = listOf(h.connection)
        h.viewModel.complete()
        advanceUntilIdle()
        coVerify { h.layout.setLayout(HomeLayout.CLASSIC) }
        coVerify { h.experience.setMode(ExperienceMode.ESSENTIAL) }
        coVerify { h.player.setStreamAutoPlayMode(StreamAutoPlayMode.FIRST_STREAM) }
        coVerify { h.onboarding.setHasSeenAuthQrOnFirstLaunch(true) }
        verify(exactly = 1) { h.preferences.completeServerSetup() }
        assertFalse(h.viewModel.failed.value)
        assertFalse(h.viewModel.finishing.value)
    }

    @Test fun existingInstallationKeepsLayoutAndPlaybackChoices() = runTest(dispatcher) {
        val h = Harness(existing = true)
        h.viewModel.complete()
        advanceUntilIdle()
        coVerify(exactly = 0) { h.layout.setLayout(any()) }
        coVerify(exactly = 0) { h.experience.setMode(any()) }
        coVerify(exactly = 0) { h.player.setStreamAutoPlayMode(any()) }
        coVerify(exactly = 0) { h.trailers.setEnabled(any()) }
        verify { h.preferences.completeServerSetup() }
    }

    @Test fun failedSaveDoesNotMarkSetupCompleteAndCanBeRetried() = runTest(dispatcher) {
        val h = Harness()
        h.connections = listOf(h.connection)
        coEvery { h.layout.setLayout(any()) } throws IOException("test")
        h.viewModel.complete()
        advanceUntilIdle()
        assertTrue(h.viewModel.failed.value)
        verify(exactly = 0) { h.preferences.completeServerSetup() }
        coEvery { h.layout.setLayout(any()) } just Runs
        h.viewModel.complete()
        advanceUntilIdle()
        assertFalse(h.viewModel.failed.value)
        verify(exactly = 1) { h.preferences.completeServerSetup() }
    }

    @Test fun setupCannotFinishWithEveryLibraryUnselected() = runTest(dispatcher) {
        val h = Harness(existing = true, selected = false)
        h.viewModel.complete()
        advanceUntilIdle()
        verify(exactly = 0) { h.preferences.completeServerSetup() }
    }
}
