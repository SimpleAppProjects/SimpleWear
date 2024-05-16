package com.thewizrd.simplewear.ui.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSaveLayer
import com.google.android.horologist.compose.ambient.AmbientState
import kotlin.random.Random

/**
 * Number of pixels to offset the content rendered in the display to prevent screen burn-in.
 */
private const val BURN_IN_OFFSET_PX = 10

/**
 * If the screen requires burn-in protection, items must be shifted around periodically
 * in ambient mode. To ensure that content isn't shifted off the screen, avoid placing
 * content within 10 pixels of the edge of the screen.
 *
 * Activities should also avoid solid white areas to prevent pixel burn-in. Both of
 * these requirements only apply in ambient mode, and only when
 * [AmbientState.Ambient.doBurnInProtection] is set to true.
 */
fun Modifier.ambientMode(
    ambientState: AmbientState
): Modifier = composed {
    val translationX = rememberBurnInTranslation(ambientState)
    val translationY = rememberBurnInTranslation(ambientState)

    this
        .graphicsLayer {
            this.translationX = translationX
            this.translationY = translationY
        }
        .ambientGray(ambientState)
}

@Composable
private fun rememberBurnInTranslation(
    ambientState: AmbientState
): Float =
    remember(ambientState) {
        when (ambientState) {
            AmbientState.Interactive -> 0f
            is AmbientState.Ambient -> if (ambientState.ambientDetails?.burnInProtectionRequired == true) {
                Random.nextInt(-BURN_IN_OFFSET_PX, BURN_IN_OFFSET_PX + 1).toFloat()
            } else {
                0f
            }
        }
    }

private val grayscale = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToSaturation(0f)
        }
    )
    isAntiAlias = false
}

internal fun Modifier.ambientGray(ambientState: AmbientState): Modifier =
    if (ambientState is AmbientState.Ambient) {
        graphicsLayer {
            scaleX = 0.9f
            scaleY = 0.9f
        }.drawWithContent {
            drawIntoCanvas {
                it.withSaveLayer(size.toRect(), grayscale) {
                    drawContent()
                }
            }
        }
    } else {
        this
    }