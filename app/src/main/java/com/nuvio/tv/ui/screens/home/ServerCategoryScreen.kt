package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.theme.NuvioTheme

// The category destinations share Home's loaded rows, ordering and visibility settings.
// Only their focus state is separate, so switching tabs does not duplicate catalogs in memory.
class ServerCategoryFocusState : ViewModel() {
    var focus by mutableStateOf(HomeScreenFocusState())
}

internal fun serverCategoryRows(rows: List<HomeRow>, contentType: String): List<HomeRow> =
    rows.mapNotNull { homeRow ->
        when (homeRow) {
            is HomeRow.Catalog -> homeRow.row.takeIf {
                ServerCatalog.isServerAddon(it.addonBaseUrl) && it.apiType == contentType && it.catalogId != "resume"
            }?.let { row ->
                HomeRow.Catalog(row.copy(catalogName = serverCategoryTitle(row)))
            }
            is HomeRow.PlaceholderCatalog -> homeRow.takeIf {
                ServerCatalog.isServerAddon(it.addonBaseUrl) && it.apiType == contentType && it.catalogId != "resume"
            }?.let { row ->
                val title = row.catalogName.substringAfter(" · ")
                HomeRow.Catalog(CatalogRow(
                    addonId = row.addonId, addonName = row.addonName, addonBaseUrl = row.addonBaseUrl,
                    catalogId = row.catalogId, catalogName = title,
                    type = ContentType.fromString(row.apiType), rawType = row.apiType,
                    items = (0 until 8).map { index ->
                        MetaPreview(
                            id = "__placeholder_${row.catalogKey}_$index",
                            type = ContentType.fromString(row.apiType), rawType = row.apiType,
                            name = "", poster = PLACEHOLDER_IMAGE_URL, posterShape = PosterShape.POSTER,
                            background = null, logo = null, description = null, releaseInfo = null,
                            imdbRating = null, genres = emptyList()
                        )
                    },
                    isLoading = true, hasMore = false
                ))
            }
            is HomeRow.CollectionRow -> null
        }
    }

private fun serverCategoryTitle(row: CatalogRow): String =
    row.catalogName.substringAfter(" · ")

@Composable
fun ServerCategoryScreen(
    contentType: String,
    homeViewModel: HomeViewModel,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit,
    categoryFocus: ServerCategoryFocusState = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, homeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeViewModel.refreshHomeCatalogsIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val rows = remember(state.homeRows, contentType) { serverCategoryRows(state.homeRows, contentType) }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isLoading) LoadingIndicator()
            else Text(
                stringResource(R.string.emby_category_empty),
                modifier = Modifier.padding(32.dp),
                color = NuvioTheme.colors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    val categoryState = remember(state, rows) {
        state.copy(
            homeRows = rows,
            catalogRows = rows.filterIsInstance<HomeRow.Catalog>().map { it.row },
            heroItems = emptyList(),
            heroSectionEnabled = false,
            continueWatchingItems = emptyList(),
            upcomingItems = emptyList(),
            continueWatchingEnabled = false,
            catalogTypeSuffixEnabled = false,
            catalogAddonNameEnabled = false,
            posterLabelsEnabled = true,
            focusedPosterBackdropExpandEnabled = false,
            focusedPosterBackdropTrailerEnabled = false
        )
    }
    ClassicHomeContent(
        uiState = categoryState,
        posterCardStyle = PosterCardDefaults.Style.copy(
            width = state.posterCardWidthDp.dp,
            height = (state.posterCardWidthDp * 1.5f).dp,
            cornerRadius = state.posterCardCornerRadiusDp.dp
        ),
        focusState = categoryFocus.focus,
        trailerPreviewUrls = emptyMap(),
        trailerPreviewAudioUrls = emptyMap(),
        onNavigateToDetail = { id, type, addon ->
            if (!id.startsWith("__placeholder_")) onNavigateToDetail(id, type, addon)
        },
        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
        onContinueWatchingClick = {},
        onRemoveContinueWatching = { _, _, _, _ -> },
        onRequestTrailerPreview = {},
        onRequestLazyCatalogLoad = homeViewModel::requestLazyCatalogLoad,
        onSaveFocusState = { vi, vo, key, items, positions, anchors, ri, ii ->
            categoryFocus.focus = HomeScreenFocusState(
                verticalScrollIndex = vi, verticalScrollOffset = vo,
                focusedRowKey = key, focusedItemKeyByRow = items,
                catalogRowScrollStates = positions, catalogRowScrollAnchors = anchors,
                focusedRowIndex = ri, focusedItemIndex = ii, hasSavedFocus = true
            )
        }
    )
}
