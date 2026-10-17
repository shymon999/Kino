@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.nuvio.tv.updater

import android.content.Context
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ForkUpdateIsolationTest {
    @Test fun forkNeverChecksTheUpstreamUpdateRepositoryEvenOnForcedCheck() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = mockk<UpdateRepository>()
            val preferences = mockk<UpdatePreferences>()
            val downloader = mockk<ApkDownloader>()
            val viewModel = UpdateViewModel(mockk<Context>(), repository, preferences, downloader)
            advanceUntilIdle()
            viewModel.checkForUpdates(force = true, showNoUpdateFeedback = true)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.updateBannerEnabled)
            assertFalse(viewModel.uiState.value.showBanner)
            verify { repository wasNot Called }
            verify { preferences wasNot Called }
            verify { downloader wasNot Called }
        } finally { Dispatchers.resetMain() }
    }
}
