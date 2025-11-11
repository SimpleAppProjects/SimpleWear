package com.thewizrd.simplewear.ui.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.ScrollIndicatorColors
import androidx.wear.compose.material3.ScrollIndicatorDefaults

@Composable
fun LazyGridScrollIndicator(
    lazyGridState: LazyGridState,
    modifier: Modifier = Modifier,
    colors: ScrollIndicatorColors = ScrollIndicatorDefaults.colors(),
    reverseDirection: Boolean = false,
    positionAnimationSpec: AnimationSpec<Float> = ScrollIndicatorDefaults.PositionAnimationSpec,
) = androidx.wear.compose.material3.ScrollIndicator(
    state = rememberLazyGridScrollState(lazyGridState),
    modifier = modifier,
    colors = colors,
    reverseDirection = reverseDirection,
    positionAnimationSpec = positionAnimationSpec
)