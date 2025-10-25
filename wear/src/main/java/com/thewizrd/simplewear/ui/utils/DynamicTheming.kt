/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thewizrd.simplewear.ui.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import androidx.wear.compose.material3.MaterialTheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDominantColorState(
    context: Context = LocalContext.current,
    defaultColor: Color = MaterialTheme.colorScheme.primary,
    defaultOnColor: Color = MaterialTheme.colorScheme.onPrimary,
    cacheSize: Int = 12,
    isColorValid: (Color) -> Boolean = { true }
): DominantColorState = remember {
    DominantColorState(context, defaultColor, defaultOnColor, cacheSize, isColorValid)
}

/**
 * A composable which allows dynamic theming of the [androidx.compose.material.Colors.primary]
 * color from an image.
 */
@SuppressLint("RestrictedApi")
@Composable
fun DynamicThemePrimaryColorsFromImage(
    dominantColorState: DominantColorState = rememberDominantColorState(),
    content: @Composable () -> Unit
) {
    val color = animateColorAsState(
        dominantColorState.color,
        spring(stiffness = Spring.StiffnessLow), label = "primary"
    ).value

    val scheme = remember(color) {
        SchemeContent(Hct.fromInt(color.toArgb()), true, 0.0)
    }

    val colors = MaterialTheme.colorScheme.copy(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        primaryDim = Color(scheme.primaryFixedDim),

        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        secondaryDim = Color(scheme.secondaryFixedDim),

        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        tertiaryDim = Color(scheme.tertiaryFixedDim),

        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerLow = Color(scheme.surfaceContainerLow),
        onSurface = Color(scheme.onSurface),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),

        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),

        error = Color(scheme.error),
        errorContainer = Color(scheme.errorContainer),
        onError = Color(scheme.onError),
        onErrorContainer = Color(scheme.onErrorContainer),

        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
    )

    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * A class which stores and caches the result of any calculated dominant colors
 * from images.
 *
 * @param context Android context
 * @param defaultColor The default color, which will be used if [calculateDominantColor] fails to
 * calculate a dominant color
 * @param defaultOnColor The default foreground 'on color' for [defaultColor].
 * @param cacheSize The size of the [LruCache] used to store recent results. Pass `0` to
 * disable the cache.
 * @param isColorValid A lambda which allows filtering of the calculated image colors.
 */
@Stable
class DominantColorState(
    private val context: Context,
    private val defaultColor: Color,
    private val defaultOnColor: Color,
    cacheSize: Int = 12,
    private val isColorValid: (Color) -> Boolean = { true }
) {
    var color by mutableStateOf(defaultColor)
        private set
    var onColor by mutableStateOf(defaultOnColor)
        private set

    private val cache = when {
        cacheSize > 0 -> LruCache<String, DominantColors>(cacheSize)
        else -> null
    }

    suspend fun updateColorsFromImage(key: String, bitmap: Bitmap?, useCache: Boolean = true) {
        val result = calculateDominantColor(key, bitmap, useCache)
        color = result?.color ?: defaultColor
        onColor = result?.onColor ?: defaultOnColor
    }

    private suspend fun calculateDominantColor(
        key: String,
        bitmap: Bitmap?,
        useCache: Boolean = true
    ): DominantColors? {
        if (useCache) {
            val cached = cache?.get(key)
            if (cached != null) {
                // If we already have the result cached, return early now...
                return cached
            }
        }

        // Otherwise we calculate the swatches in the image, and return the first valid color
        return calculateSwatchesInImage(context, bitmap)
            // First we want to sort the list by the color's population
            .sortedByDescending { swatch -> swatch.population }
            // Then we want to find the first valid color
            .firstOrNull { swatch -> isColorValid(Color(swatch.rgb)) }
            // If we found a valid swatch, wrap it in a [DominantColors]
            ?.let { swatch ->
                DominantColors(
                    color = Color(swatch.rgb),
                    onColor = Color(swatch.bodyTextColor).copy(alpha = 1f)
                )
            }
            // Cache the resulting [DominantColors]
            ?.also { result -> cache?.put(key, result) }
    }

    /**
     * Reset the color values to [defaultColor].
     */
    fun reset() {
        color = defaultColor
        onColor = defaultColor
    }
}

@Immutable
private data class DominantColors(val color: Color, val onColor: Color)

/**
 * Uses [Palette] to calculate the dominant color.
 */
private suspend fun calculateSwatchesInImage(
    context: Context,
    bitmap: Bitmap?
): List<Palette.Swatch> {
    return bitmap?.let {
        withContext(Dispatchers.Default) {
            val palette = Palette.Builder(bitmap)
                // Disable any bitmap resizing in Palette. We've already loaded an appropriately
                // sized bitmap through Coil
                .resizeBitmapArea(0)
                // Clear any built-in filters. We want the unfiltered dominant color
                .clearFilters()
                // We reduce the maximum color count down to 8
                .maximumColorCount(8)
                .generate()

            palette.swatches
        }
    } ?: emptyList()
}

