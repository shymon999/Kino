@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.PosterPreferencesDataStore
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.account.InputField
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun KinoPosterSettingsDialog(store: PosterPreferencesDataStore, onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(store.state.value.polishPosters) }
    var key by remember { mutableStateOf(store.savedApiKey()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val save: (Boolean, String) -> Unit = { useTmdb, value ->
        if (!saving) {
            val trimmed = value.trim()
            if (!PosterPreferencesDataStore.validApiKey(trimmed) || (useTmdb && trimmed.isEmpty())) {
                error = R.string.kino_tmdb_key_invalid
            } else {
                saving = true
                error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.save(useTmdb, trimmed) }
                        key = ""
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = R.string.kino_tmdb_save_failed
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }
    NuvioDialog(onDismiss = { if (!saving) onDismiss() }, title = stringResource(R.string.kino_posters),
        subtitle = stringResource(R.string.kino_posters_description), width = 620.dp) {
        SettingsToggleRow(title = stringResource(R.string.kino_posters_tmdb), subtitle = null,
            checked = enabled, enabled = !saving, onToggle = { enabled = !enabled })
        InputField(value = key, onValueChange = { if (!saving) { key = it; error = null } },
            placeholder = stringResource(R.string.kino_tmdb_key), isPassword = true,
            keyboardType = KeyboardType.Password, modifier = Modifier.fillMaxWidth(),
            onImeAction = { save(enabled, key) })
        Text(stringResource(R.string.kino_tmdb_key_help), color = NuvioTheme.colors.TextSecondary)
        error?.let { Text(stringResource(it), color = NuvioTheme.colors.Error) }
        SettingsDialogActionRow {
            SettingsDialogActionButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, enabled = !saving)
            SettingsDialogActionButton(text = stringResource(R.string.kino_tmdb_remove),
                onClick = { save(false, "") }, enabled = !saving && store.state.value.hasApiKey)
            SettingsDialogActionButton(text = stringResource(R.string.action_save),
                onClick = { save(enabled, key) }, primary = true, enabled = !saving)
        }
    }
}
