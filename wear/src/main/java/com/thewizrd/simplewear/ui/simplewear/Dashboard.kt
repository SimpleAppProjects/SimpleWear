package com.thewizrd.simplewear.ui.simplewear

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.google.android.horologist.compose.layout.scrollAway
import com.thewizrd.simplewear.ui.theme.WearAppTheme
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.viewmodels.DashboardViewModel

@Composable
fun Dashboard(
    modifier: Modifier = Modifier
) {
    val dashboardViewModel = activityViewModel<DashboardViewModel>()

    val scrollState = rememberScrollState()

    WearAppTheme {
        Scaffold(
            modifier = modifier.background(MaterialTheme.colors.background),
            timeText = {
                TimeText(modifier = Modifier.scrollAway { scrollState })
            },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scrollState = scrollState) }
        ) {
            DashboardScreen(
                dashboardViewModel = dashboardViewModel,
                scrollState = scrollState
            )
        }
    }
}