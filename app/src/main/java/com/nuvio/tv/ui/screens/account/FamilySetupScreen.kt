package com.nuvio.tv.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.mediaserver.FamilySetupState
import com.nuvio.tv.data.mediaserver.ServerFailure
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun FamilySetupScreen(state: FamilySetupState, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(state.loading) {
        if (!state.loading) retryFocus.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 680.dp).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(stringResource(R.string.family_setup_title), style = MaterialTheme.typography.headlineMedium)
            if (state.loading) {
                LoadingIndicator()
                Text(stringResource(R.string.family_setup_loading), textAlign = TextAlign.Center)
            } else {
                val message = when (state.failure) {
                    ServerFailure.AUTH_REQUIRED, ServerFailure.FORBIDDEN -> R.string.family_setup_auth_error
                    ServerFailure.INCOMPLETE -> R.string.family_setup_configuration_error
                    else -> R.string.family_setup_connection_error
                }
                Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                    Text(stringResource(R.string.family_setup_retry))
                }
            }
        }
    }
}
