@file:OptIn(ExperimentalLayoutApi::class, ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.Button
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.GestureActionState
import com.thewizrd.shared_resources.helpers.GestureUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.components.SwipeToDismissPagerScreen
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.viewmodels.GestureUiState
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
    navController: NavController,
    swipeToDismissBoxState: SwipeToDismissBoxState = rememberSwipeToDismissBoxState()
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureUiViewModel = activityViewModel<GestureUiViewModel>()
    val uiState by gestureUiViewModel.uiState.collectAsState()

    val pagerState = rememberPagerState {
        if (uiState.actionState.accessibilityEnabled && uiState.actionState.keyEventSupported) 2 else 1
    }

    val isRoot = navController.previousBackStackEntry == null

    SwipeToDismissPagerScreen(
        modifier = modifier.background(MaterialTheme.colors.background),
        isRoot = isRoot,
        swipeToDismissBoxState = swipeToDismissBoxState,
        state = pagerState,
        timeText = {
            if (!uiState.isLoading) TimeText()
        },
        hidePagerIndicator = uiState.isLoading
    ) { pageIdx ->
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
            when (pageIdx) {
                // Gestures
                0 -> {
                    GestureScreen(
                        modifier = modifier,
                        uiState = uiState,
                        onDPadDirection = { direction ->
                            when (direction) {
                                KeyEvent.KEYCODE_DPAD_UP -> gestureUiViewModel.requestDPad(top = 1)
                                KeyEvent.KEYCODE_DPAD_DOWN -> gestureUiViewModel.requestDPad(bottom = 1)
                                KeyEvent.KEYCODE_DPAD_LEFT -> gestureUiViewModel.requestDPad(left = 1)
                                KeyEvent.KEYCODE_DPAD_RIGHT -> gestureUiViewModel.requestDPad(right = 1)
                            }
                        },
                        onDPadClicked = {
                            gestureUiViewModel.requestDPadClick()
                        },
                        onScroll = { dX, dY, screenWidth, screenHeight ->
                            gestureUiViewModel.requestScroll(dX, dY, screenWidth, screenHeight)
                        }
                    )
                }
                // Buttons
                1 -> {
                    ButtonScreen(
                        modifier = modifier,
                        onKeyPressed = { keyEvent ->
                            gestureUiViewModel.requestKeyEvent(keyEvent)
                        }
                    )
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
private fun GestureScreen(
    modifier: Modifier = Modifier,
    uiState: GestureUiState,
    onDPadDirection: ((Int) -> Unit) = {},
    onDPadClicked: () -> Unit = {},
    onScroll: (dX: Float, dY: Float, screenWidth: Float, screenHeight: Float) -> Unit = { _, _, _, _ ->
    }
) {
    val context = LocalContext.current

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

    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var dispatchJob: Job? = null

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .pointerInput("horizontalScroll") {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (scrollOffset != 0f) {
                            onScroll(
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
                            onScroll(
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
                        onScroll(
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
                    onDPadDirection(KeyEvent.KEYCODE_DPAD_UP)
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
                    onDPadDirection(KeyEvent.KEYCODE_DPAD_DOWN)
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
                    onDPadDirection(KeyEvent.KEYCODE_DPAD_LEFT)
                },
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            tint = Color.White,
            contentDescription = null
        )
        Icon(
            modifier = Modifier
                .size(24.dp)
                .offset(x = -inset)
                .align(Alignment.CenterEnd)
                .clickable(uiState.actionState.dpadSupported) {
                    onDPadDirection(KeyEvent.KEYCODE_DPAD_RIGHT)
                },
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            tint = Color.White,
            contentDescription = null
        )
        if (uiState.actionState.dpadSupported) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .clickable {
                        onDPadClicked()
                    }
                    .background(Color.White, shape = RoundedCornerShape(50))
            )
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@WearPreviewDevices
@Composable
private fun ButtonScreen(
    modifier: Modifier = Modifier,
    onKeyPressed: (Int) -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            overflow = FlowRowOverflow.Visible,
        ) {
            Button(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(id = R.string.label_back),
                onClick = {
                    onKeyPressed(KeyEvent.KEYCODE_BACK)
                }
            )
            Button(
                imageVector = Icons.Outlined.Home,
                contentDescription = stringResource(id = R.string.label_home),
                onClick = {
                    onKeyPressed(KeyEvent.KEYCODE_HOME)
                }
            )
            Button(
                id = R.drawable.ic_outline_view_apps,
                contentDescription = stringResource(id = R.string.label_recents),
                onClick = {
                    onKeyPressed(KeyEvent.KEYCODE_APP_SWITCH)
                }
            )
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
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

@WearPreviewDevices
@Composable
private fun PreviewGestureScreen() {
    val uiState = remember {
        GestureUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            isLoading = false,
            actionState = GestureActionState(dpadSupported = true)
        )
    }

    GestureScreen(uiState = uiState)
}