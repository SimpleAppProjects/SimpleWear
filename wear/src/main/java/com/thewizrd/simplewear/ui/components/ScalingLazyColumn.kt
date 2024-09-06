package com.thewizrd.simplewear.ui.components

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumnState
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.compose.rotaryinput.rememberRotaryHapticHandler
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll

@ExperimentalWearFoundationApi
@ExperimentalHorologistApi
@Composable
fun ScalingLazyColumn(
    modifier: Modifier = Modifier,
    scrollState: ScalingLazyColumnState = rememberResponsiveColumnState(),
    focusRequester: FocusRequester = rememberActiveFocusRequester(),
    content: ScalingLazyListScope.() -> Unit
) {
    androidx.wear.compose.foundation.lazy.ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .rotaryWithScroll(
                focusRequester = focusRequester,
                scrollableState = scrollState.state,
                reverseDirection = scrollState.reverseLayout,
                rotaryHaptics = rememberRotaryHapticHandler(scrollState)
            ),
        state = scrollState.state,
        contentPadding = scrollState.contentPadding,
        reverseLayout = scrollState.reverseLayout,
        verticalArrangement = scrollState.verticalArrangement,
        horizontalAlignment = scrollState.horizontalAlignment,
        flingBehavior = scrollState.flingBehavior
            ?: ScrollableDefaults.flingBehavior(),
        userScrollEnabled = scrollState.userScrollEnabled,
        scalingParams = scrollState.scalingParams,
        anchorType = scrollState.anchorType,
        autoCentering = scrollState.autoCentering,
        content = content
    )
}