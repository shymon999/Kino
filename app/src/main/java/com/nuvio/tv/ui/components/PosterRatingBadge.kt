package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.Locale

@Composable
fun PosterRatingBadge(rating: Float?, modifier: Modifier = Modifier) {
    if (rating == null || !rating.isFinite() || rating <= 0f || rating > 10f) return
    // Emby's community rating does not identify its metadata provider. Avoid an IMDb logo.
    Text(
        text = String.format(Locale.getDefault(), "★ %.1f", rating),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
