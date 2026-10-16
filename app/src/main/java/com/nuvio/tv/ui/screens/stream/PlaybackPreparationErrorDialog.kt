package com.nuvio.tv.ui.screens.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlaybackPreparationErrorDialog(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.player_error_playback_fallback),
        subtitle = message,
        suppressFirstKeyUp = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            if (canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).focusRequester(initialFocus)
                ) { Text(stringResource(R.string.action_retry)) }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).then(
                    if (canRetry) Modifier else Modifier.focusRequester(initialFocus)
                )
            ) { Text(stringResource(R.string.action_close)) }
        }
    }
}
