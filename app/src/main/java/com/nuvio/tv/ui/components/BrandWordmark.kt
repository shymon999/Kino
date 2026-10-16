package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.brandWordmarkResource

@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    drawableOverride: Int? = null
) {
    val source = ImageBitmap.imageResource(drawableOverride ?: NuvioTheme.currentTheme.brandWordmarkResource)
    val painter = remember(source) { KinoWordmarkPainter(source) }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha
    )
}

/** Reuses the original symbol and draws the fork name without changing upstream artwork. */
private class KinoWordmarkPainter(private val source: ImageBitmap) : Painter() {
    override val intrinsicSize = Size(900f, 344f)
    private val lettering = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 224f
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }

    override fun DrawScope.onDraw() {
        scale(size.width / intrinsicSize.width, size.height / intrinsicSize.height,
            pivot = androidx.compose.ui.geometry.Offset.Zero) {
            // The symbol occupies the left 314 px of the original 1086 x 344 wordmark.
            drawImage(source, srcSize = IntSize((source.width * 314f / 1086f).toInt(), source.height),
                dstSize = IntSize(314, 344))
            drawIntoCanvas { canvas ->
                val baseline = 172f - (lettering.ascent() + lettering.descent()) / 2f
                canvas.nativeCanvas.drawText("Kino", 370f, baseline, lettering)
            }
        }
    }
}
