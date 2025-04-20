package com.thewizrd.simplewear.ui.components

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.dialog.DialogDefaults
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberColumnState
import com.google.android.horologist.compose.material.ConfirmationContent
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import kotlinx.coroutines.delay

@OptIn(ExperimentalHorologistApi::class, ExperimentalAnimationGraphicsApi::class)
@Composable
fun ConfirmationOverlay(
    confirmationData: ConfirmationData?,
    onTimeout: () -> Unit,
    showDialog: Boolean = confirmationData != null
) {
    val currentOnDismissed by rememberUpdatedState(onTimeout)
    val durationMillis = remember(confirmationData) {
        confirmationData?.durationMs ?: DialogDefaults.ShortDurationMillis
    }

    val a11yDurationMillis = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = durationMillis,
        containsIcons = confirmationData?.iconResId != null,
        containsText = confirmationData?.title != null,
        containsControls = false,
    ) ?: durationMillis

    val columnState = rememberColumnState(
        ScalingLazyColumnDefaults.responsive(
            verticalArrangement = Arrangement.spacedBy(
                space = 4.dp,
                alignment = Alignment.CenterVertically
            ),
            additionalPaddingAtBottom = 0.dp,
        ),
    )

    LaunchedEffect(a11yDurationMillis, confirmationData) {
        if (showDialog) {
            delay(a11yDurationMillis)
            currentOnDismissed()
        }
    }

    Dialog(
        showDialog = showDialog,
        onDismissRequest = currentOnDismissed,
        scrollState = columnState.state,
    ) {
        ConfirmationContent(
            icon = confirmationData?.animatedVectorResId?.let { iconResId ->
                {
                    val image = AnimatedImageVector.animatedVectorResource(iconResId)
                    var atEnd by remember { mutableStateOf(false) }

                    Icon(
                        modifier = Modifier.size(48.dp),
                        painter = rememberAnimatedVectorPainter(image, atEnd),
                        contentDescription = null
                    )

                    LaunchedEffect(iconResId) {
                        atEnd = !atEnd
                    }
                }
            } ?: confirmationData?.iconResId?.let { iconResId ->
                {
                    Icon(
                        modifier = Modifier.size(48.dp),
                        painter = painterResource(iconResId),
                        contentDescription = null
                    )
                }
            },
            title = confirmationData?.title,
            columnState = columnState,
            showPositionIndicator = false,
        )
    }
}