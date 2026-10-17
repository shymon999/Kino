@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.data.local.KinoPreferences
import com.nuvio.tv.data.local.KinoPreferencesDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.PosterPreferencesDataStore
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.domain.model.HomeImdbRatingsVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class KinoSettingsViewModel @Inject constructor(
    val preferences: KinoPreferencesDataStore,
    val catalog: ServerCatalog,
    private val player: PlayerSettingsDataStore,
    private val layout: LayoutPreferenceDataStore,
    val posters: PosterPreferencesDataStore
) : ViewModel() {
    val autoQuality = player.playerSettings.map { it.serverAutoBitrateEnabled }
    val ratings = layout.homeImdbRatingsVisibility.map { it.showRatings }
    fun setAutoQuality(enabled: Boolean) { viewModelScope.launch { player.setServerAutoBitrateEnabled(enabled) } }
    fun setRatings(enabled: Boolean) { viewModelScope.launch {
        layout.setHomeImdbRatingsVisibility(if (enabled) HomeImdbRatingsVisibility.SHOW_ALL else HomeImdbRatingsVisibility.HIDE_ALL)
    } }
}

@Composable
internal fun KinoSettingsContent(
    initialFocusRequester: FocusRequester?,
    onCatalogOrder: () -> Unit,
    viewModel: KinoSettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.state.collectAsStateWithLifecycle()
    val autoQuality by viewModel.autoQuality.collectAsStateWithLifecycle(initialValue = false)
    val ratings by viewModel.ratings.collectAsStateWithLifecycle(initialValue = true)
    val posters by viewModel.posters.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<String?>(null) }
    var showServers by remember { mutableStateOf(false) }
    var connectionId by remember { mutableStateOf<String?>(null) }
    BackHandler(showServers) {
        if (connectionId != null) connectionId = null else showServers = false
    }
    if (showServers) {
        connectionId?.let { id ->
            MediaServerSettingsContent(id, initialFocusRequester, { connectionId = it }, { connectionId = null })
        } ?: MediaServersSettingsContent(initialFocusRequester, { connectionId = it })
        return
    }
    val genres = viewModel.catalog.availableGenres()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        SettingsDetailHeader(stringResource(R.string.kino_settings), stringResource(R.string.kino_settings_description))
        SettingsGroupCard(Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = stringResource(R.string.kino_bitrate), subtitle = stringResource(R.string.kino_bitrate_description),
                value = preferences.bitrateMbps?.let { "$it Mb/s" } ?: stringResource(R.string.kino_unlimited),
                onClick = { picker = "bitrate" },
                modifier = initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
            )
            SettingsToggleRow(title = stringResource(R.string.server_auto_bitrate),
                subtitle = stringResource(R.string.kino_auto_quality_description), checked = autoQuality,
                onToggle = { viewModel.setAutoQuality(!autoQuality) })
            SettingsActionRow(title = stringResource(R.string.kino_hidden_genres),
                subtitle = stringResource(R.string.kino_hidden_genres_description),
                value = preferences.hiddenGenres.size.toString(), enabled = genres.isNotEmpty(),
                onClick = { picker = "genres" })
            SettingsActionRow(title = stringResource(R.string.kino_catalog_order),
                subtitle = stringResource(R.string.kino_catalog_order_description), onClick = onCatalogOrder)
            SettingsToggleRow(title = stringResource(R.string.kino_poster_ratings), subtitle = null,
                checked = ratings, onToggle = { viewModel.setRatings(!ratings) })
            SettingsActionRow(title = stringResource(R.string.kino_posters), subtitle = null,
                value = stringResource(if (posters.polishPosters && posters.hasApiKey) R.string.kino_posters_tmdb else R.string.kino_posters_emby),
                onClick = { picker = "posters" })
            SettingsActionRow(title = stringResource(R.string.settings_media_servers),
                subtitle = stringResource(R.string.kino_servers_description), onClick = { showServers = true })
        }
    }
    if (picker == "posters") KinoPosterSettingsDialog(viewModel.posters, onDismiss = { picker = null })
    if (picker == "bitrate") SettingsSingleChoiceDialog<Int?>(
        title = stringResource(R.string.kino_bitrate),
        options = listOf(SettingsPickerOption<Int?>(null, stringResource(R.string.kino_unlimited))) +
            KinoPreferences.bitrateOptions.map { SettingsPickerOption<Int?>(it, "$it Mb/s") },
        selectedValue = preferences.bitrateMbps,
        onOptionSelected = { viewModel.preferences.setBitrateMbps(it); picker = null }, onDismiss = { picker = null }
    )
    if (picker == "genres") SettingsMultiChoiceDialog(
        title = stringResource(R.string.kino_hidden_genres),
        subtitle = stringResource(R.string.kino_hidden_genres_description),
        options = genres.map { SettingsPickerOption(it.first, it.second) },
        selectedValues = preferences.hiddenGenres.toList(),
        onValuesSelected = { viewModel.preferences.setHiddenGenres(it.toSet()); picker = null },
        onDismiss = { picker = null }
    )
}
