package com.nuvio.tv.ui.screens.stream

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackPreparationErrorDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun failureStaysVisibleAndRetryHasInitialRemoteFocus() {
        var retried = 0
        compose.setContent {
            NuvioTheme {
                PlaybackPreparationErrorDialog(
                    message = "Serwer jest niedostępny",
                    canRetry = true,
                    onRetry = { retried++ },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.action_retry)).assertIsFocused()
        // Unlike a toast, the error must survive the passage of the UI clock.
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("Serwer jest niedostępny").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_retry))
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, retried) }
    }

    @Test fun failureWithoutRetryTargetFocusesCloseAndCanBeDismissed() {
        var dismissed = 0
        compose.setContent {
            NuvioTheme {
                PlaybackPreparationErrorDialog(
                    message = "Nie udało się otworzyć odtwarzacza",
                    canRetry = false,
                    onRetry = {},
                    onDismiss = { dismissed++ }
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.action_close)).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, dismissed) }
    }
}
