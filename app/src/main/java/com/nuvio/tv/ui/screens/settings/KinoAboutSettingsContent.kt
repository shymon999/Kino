@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.BrandWordmark
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun KinoAboutSettingsContent(onLicenses: () -> Unit, initialFocusRequester: FocusRequester?) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsDetailHeader(stringResource(R.string.kino_about_title), stringResource(R.string.kino_about_version, BuildConfig.VERSION_NAME))
        BrandWordmark(Modifier.height(48.dp), contentDescription = "Kino")
        Text(stringResource(R.string.kino_about_description), style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextPrimary)
        SettingsActionRow(title = stringResource(R.string.kino_upstream),
            subtitle = "github.com/NuvioMedia/NuvioTV", onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/NuvioMedia/NuvioTV"))) }
            }, modifier = initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        SettingsActionRow(title = stringResource(R.string.about_licenses_attributions),
            subtitle = stringResource(R.string.about_licenses_attributions_subtitle), onClick = onLicenses)
    }
}
