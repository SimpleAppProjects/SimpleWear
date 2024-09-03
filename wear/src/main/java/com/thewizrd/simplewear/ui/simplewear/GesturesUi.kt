package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.GestureUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.viewmodels.GestureUiViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun GesturesUi(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val viewConfig = remember(context) {
        ViewConfiguration.get(context)
    }
    val screenHeightPx = remember(context) {
        context.resources.displayMetrics.heightPixels
    }
    val screenWidthPx = remember(context) {
        context.resources.displayMetrics.widthPixels
    }

    val focusRequester = remember { FocusRequester() }

    val config = LocalConfiguration.current
    val inset = remember(config) {
        if (config.isScreenRound) {
            val screenHeightDp = config.screenHeightDp
            val screenWidthDp = config.smallestScreenWidthDp
            val maxSquareEdge = (sqrt(((screenHeightDp * screenWidthDp) / 2).toDouble()))
            Dp(((screenHeightDp - maxSquareEdge) / 2).toFloat())
        } else {
            12.dp
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureUiViewModel = activityViewModel<GestureUiViewModel>()
    val uiState by gestureUiViewModel.uiState.collectAsState()

    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var dispatchJob: Job? = null

    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = {
            if (!uiState.isLoading) TimeText()
        },
    ) {
        LoadingContent(
            empty = !uiState.actionState.accessibilityEnabled,
            emptyContent = {
                NoAccessibilityScreen(
                    onRefresh = {
                        gestureUiViewModel.refreshState()
                    }
                )
            },
            loading = uiState.isLoading
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .pointerInput("horizontalScroll") {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (scrollOffset != 0f) {
                                    gestureUiViewModel.requestScroll(
                                        scrollOffset,
                                        0f,
                                        screenWidthPx.toFloat(),
                                        screenHeightPx.toFloat()
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()

                            scrollOffset = if (dragAmount > 0) {
                                max(scrollOffset, dragAmount + viewConfig.scaledTouchSlop)
                            } else {
                                min(scrollOffset, dragAmount + -viewConfig.scaledTouchSlop)
                            }

                            dispatchJob?.cancel()
                        }
                    }
                    .pointerInput("verticalScroll") {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (scrollOffset != 0f) {
                                    gestureUiViewModel.requestScroll(
                                        0f,
                                        scrollOffset,
                                        screenWidthPx.toFloat(),
                                        screenHeightPx.toFloat()
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()

                            scrollOffset = if (dragAmount > 0) {
                                max(scrollOffset, dragAmount + viewConfig.scaledTouchSlop)
                            } else {
                                min(scrollOffset, dragAmount + -viewConfig.scaledTouchSlop)
                            }

                            dispatchJob?.cancel()
                        }
                    }
                    .onRotaryScrollEvent { event ->
                        val scrollPx = event.verticalScrollPixels

                        scrollOffset = if (scrollPx > 0) {
                            max(scrollOffset, scrollPx)
                        } else {
                            min(scrollOffset, scrollPx)
                        }

                        dispatchJob?.cancel()

                        dispatchJob = lifecycleOwner.lifecycleScope.launch {
                            delay((scrollPx.absoluteValue / viewConfig.scaledMaximumFlingVelocity).toLong())

                            if (isActive) {
                                gestureUiViewModel.requestScroll(
                                    0f,
                                    scrollOffset,
                                    screenWidthPx.toFloat(),
                                    screenHeightPx.toFloat()
                                )
                            }
                        }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable()
            ) {
                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(y = inset)
                        .align(Alignment.TopCenter)
                        .clickable(uiState.actionState.dpadSupported) {
                            gestureUiViewModel.requestDPad(top = 1)
                        },
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    tint = Color.White,
                    contentDescription = null
                )
                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(y = -inset)
                        .align(Alignment.BottomCenter)
                        .clickable(uiState.actionState.dpadSupported) {
                            gestureUiViewModel.requestDPad(bottom = 1)
                        },
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    tint = Color.White,
                    contentDescription = null
                )
                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = inset)
                        .align(Alignment.CenterStart)
                        .clickable(uiState.actionState.dpadSupported) {
                            gestureUiViewModel.requestDPad(left = 1)
                        },
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    tint = Color.White,
                    contentDescription = null
                )
                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = -inset)
                        .align(Alignment.CenterEnd)
                        .clickable(uiState.actionState.dpadSupported) {
                            gestureUiViewModel.requestDPad(right = 1)
                        },
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    tint = Color.White,
                    contentDescription = null
                )
                if (uiState.actionState.dpadSupported) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                            .clickable {
                                gestureUiViewModel.requestDPadClick()
                            }
                            .background(Color.White, shape = RoundedCornerShape(50))
                    )
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            gestureUiViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableListenerViewModel.ACTION_UPDATECONNECTIONSTATUS -> {
                        val connectionStatus = WearConnectionStatus.valueOf(
                            event.data.getInt(
                                WearableListenerViewModel.EXTRA_CONNECTIONSTATUS,
                                0
                            )
                        )

                        when (connectionStatus) {
                            WearConnectionStatus.DISCONNECTED -> {
                                // Navigate
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                gestureUiViewModel.openPlayStore(activity)

                                // Navigate
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            else -> {}
                        }
                    }

                    GestureUIHelper.GestureStatusPath -> {
                        val status =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (status == ActionStatus.PERMISSION_DENIED) {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        activity,
                                        R.drawable.ws_full_sad
                                    )
                                )
                                .setMessage(activity.getString(R.string.error_permissiondenied))
                                .showOn(activity)

                            gestureUiViewModel.openAppOnPhone(activity, false)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Update statuses
        gestureUiViewModel.refreshState()
    }
}

@Composable
private fun NoAccessibilityScreen(
    onRefresh: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 14.dp),
                text = stringResource(R.string.message_accessibility_svc_disabled),
                textAlign = TextAlign.Center
            )
            CompactChip(
                label = {
                    Text(text = stringResource(id = R.string.action_refresh))
                },
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
                        contentDescription = null
                    )
                },
                onClick = onRefresh
            )
        }
    }
}