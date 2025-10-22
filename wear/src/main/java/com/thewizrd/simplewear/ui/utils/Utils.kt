package com.thewizrd.simplewear.ui.utils

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

@Composable
fun rememberFocusRequester(): FocusRequester {
    return remember { FocusRequester() }
}

@Stable
fun Modifier.fillDashboard(): Modifier = composed {
    val isRound = LocalConfiguration.current.isScreenRound
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    var bottomInset = Dp(screenHeightDp - (screenHeightDp * 0.8733032f))

    if (isRound) {
        val screenWidthDp = LocalConfiguration.current.smallestScreenWidthDp
        val maxSquareEdge = (sqrt(((screenHeightDp * screenWidthDp) / 2).toFloat()))
        bottomInset = Dp((screenHeightDp - (maxSquareEdge * 0.8733032f)) / 2)
    }

    fillMaxSize().padding(
        start = if (isRound) 14.dp else 8.dp,
        end = if (isRound) 14.dp else 8.dp,
        top = if (isRound) 36.dp else 8.dp,
        bottom = bottomInset
    )
}