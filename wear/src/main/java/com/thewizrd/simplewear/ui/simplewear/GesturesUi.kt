@file:OptIn(ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.GestureActionState
import com.thewizrd.shared_resources.helpers.GestureUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.getSerializableCompat
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.components.HorizontalPagerScreen
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.compose.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
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
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureUiViewModel = activityViewModel<GestureUiViewModel>()
    val uiState by gestureUiViewModel.uiState.collectAsState()

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    val pagerState = rememberPagerState {
        if (uiState.actionState.accessibilityEnabled && uiState.actionState.keyEventSupported) 2 else 1
    }

    HorizontalPagerScreen(
        modifier = modifier,
        pagerState = pagerState,
        hidePagerIndicator = uiState.isLoading,
    ) { pageIdx ->
        AnimatedPage(pageIdx, pagerState) {
            ScreenScaffold { contentPadding ->
                LoadingContent(
                    empty = !uiState.actionState.accessibilityEnabled,
                    emptyContent = {
                        NoAccessibilityScreen(
                            modifier = Modifier.padding(contentPadding),
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
                                modifier = modifier.padding(contentPadding),
                                uiState = uiState,
                                onDPadDirection = { direction ->
                                    when (direction) {
                                        KeyEvent.KEYCODE_DPAD_UP -> gestureUiViewModel.requestDPad(
                                            top = 1
                                        )

                                        KeyEvent.KEYCODE_DPAD_DOWN -> gestureUiViewModel.requestDPad(
                                            bottom = 1
                                        )

                                        KeyEvent.KEYCODE_DPAD_LEFT -> gestureUiViewModel.requestDPad(
                                            left = 1
                                        )

                                        KeyEvent.KEYCODE_DPAD_RIGHT -> gestureUiViewModel.requestDPad(
                                            right = 1
                                        )
                                    }
                                },
                                onDPadClicked = {
                                    gestureUiViewModel.requestDPadClick()
                                },
                                onScroll = { dX, dY, screenWidth, screenHeight ->
                                    gestureUiViewModel.requestScroll(
                                        dX,
                                        dY,
                                        screenWidth,
                                        screenHeight
                                    )
                                }
                            )
                        }
                        // Buttons
                        1 -> {
                            ButtonScreen(
                                modifier = modifier.padding(contentPadding),
                                onKeyPressed = { keyEvent ->
                                    gestureUiViewModel.requestKeyEvent(keyEvent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

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
                                gestureUiViewModel.openPlayStore()

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
                            event.data.getSerializableCompat(
                                WearableListenerViewModel.EXTRA_STATUS,
                                ActionStatus::class.java
                            )

                        if (status == ActionStatus.PERMISSION_DENIED) {
                            confirmationViewModel.showOpenOnPhoneForFailure(
                                message = context.getString(
                                    R.string.error_permissiondenied_wear
                                )
                            )

                            gestureUiViewModel.openAppOnPhone(false)
                        }
                    }

                    WearableListenerViewModel.ACTION_SHOWCONFIRMATION -> {
                        val jsonData =
                            event.data.getString(WearableListenerViewModel.EXTRA_ACTIONDATA)

                        JSONParser.deserializer(jsonData, ConfirmationData::class.java)?.let {
                            confirmationViewModel.showConfirmation(it)
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

    val focusRequester = rememberFocusRequester()

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
            imageVector = Icons.Rounded.KeyboardArrowUp,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(R.string.label_arrow_up)
        )
        Icon(
            modifier = Modifier
                .size(24.dp)
                .offset(y = -inset)
                .align(Alignment.BottomCenter)
                .clickable(uiState.actionState.dpadSupported) {
                    onDPadDirection(KeyEvent.KEYCODE_DPAD_DOWN)
                },
            imageVector = Icons.Rounded.KeyboardArrowDown,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(R.string.label_arrow_down)
        )
        Icon(
            modifier = Modifier
                .size(24.dp)
                .offset(x = inset)
                .align(Alignment.CenterStart)
                .clickable(uiState.actionState.dpadSupported) {
                    onDPadDirection(KeyEvent.KEYCODE_DPAD_LEFT)
                },
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(R.string.label_arrow_left)
        )
        Icon(
            modifier = Modifier
                .size(24.dp)
                .offset(x = -inset)
                .align(Alignment.CenterEnd)
                .clickable(uiState.actionState.dpadSupported) {
                    onDPadDirection(KeyEvent.KEYCODE_DPAD_RIGHT)
                },
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(R.string.label_arrow_right)
        )
        if (uiState.actionState.dpadSupported) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .clickable(
                        onClickLabel = stringResource(R.string.label_dpad_center)
                    ) {
                        onDPadClicked()
                    }
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
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
            FilledIconButton(
                content = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.label_back)
                    )
                },
                onClick = {
                    onKeyPressed(KeyEvent.KEYCODE_BACK)
                }
            )
            FilledIconButton(
                content = {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = stringResource(id = R.string.label_home),
                    )
                },
                onClick = {
                    onKeyPressed(KeyEvent.KEYCODE_HOME)
                }
            )
            FilledIconButton(
                content = {
                    Icon(
                        painter = painterResource(R.drawable.ic_view_apps_filled),
                        contentDescription = stringResource(id = R.string.label_recents),
                    )
                },
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
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {}
) {
    Box(
        modifier = modifier
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
            CompactButton(
                label = {
                    Text(text = stringResource(id = R.string.action_refresh))
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(id = R.string.action_refresh)
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