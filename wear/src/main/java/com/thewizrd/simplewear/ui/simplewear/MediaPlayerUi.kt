@file:OptIn(
    ExperimentalHorologistApi::class, ExperimentalFoundationApi::class,
    ExperimentalWearFoundationApi::class, ExperimentalWearMaterialApi::class
)

package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.VolumePositionIndicator
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.audio.ui.components.actions.SettingsButton
import com.google.android.horologist.audio.ui.volumeRotaryBehavior
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.compose.layout.scrollAway
import com.google.android.horologist.compose.material.Chip
import com.google.android.horologist.media.model.PlaybackStateEvent
import com.google.android.horologist.media.model.TimestampProvider
import com.google.android.horologist.media.ui.components.ControlButtonLayout
import com.google.android.horologist.media.ui.components.animated.AnimatedMediaControlButtons
import com.google.android.horologist.media.ui.components.animated.MarqueeTextMediaDisplay
import com.google.android.horologist.media.ui.components.controls.MediaButton
import com.google.android.horologist.media.ui.components.display.LoadingMediaDisplay
import com.google.android.horologist.media.ui.components.display.NothingPlayingDisplay
import com.google.android.horologist.media.ui.screens.player.PlayerScreen
import com.google.android.horologist.media.ui.state.LocalTimestampProvider
import com.google.android.horologist.media.ui.state.mapper.TrackPositionUiModelMapper
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.media.MediaItemModel
import com.thewizrd.simplewear.media.MediaPageType
import com.thewizrd.simplewear.media.MediaPlayerUiController
import com.thewizrd.simplewear.media.MediaPlayerUiState
import com.thewizrd.simplewear.media.MediaPlayerViewModel
import com.thewizrd.simplewear.media.MediaVolumeViewModel
import com.thewizrd.simplewear.media.NoopPlayerUiController
import com.thewizrd.simplewear.media.PlayerState
import com.thewizrd.simplewear.media.PlayerUiController
import com.thewizrd.simplewear.media.toPlaybackStateEvent
import com.thewizrd.simplewear.ui.ambient.ambientMode
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.components.ScalingLazyColumn
import com.thewizrd.simplewear.ui.components.SwipeToDismissPagerScreen
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun MediaPlayerUi(
    modifier: Modifier = Modifier,
    navController: NavController,
    swipeToDismissBoxState: SwipeToDismissBoxState = rememberSwipeToDismissBoxState(),
    app: AppItemViewModel? = null,
    autoLaunch: Boolean = (app == null),
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaPlayerViewModel = viewModel<MediaPlayerViewModel>()
    val volumeViewModel = remember(context, mediaPlayerViewModel) {
        MediaVolumeViewModel(
            context,
            mediaPlayerViewModel
        )
    }
    val uiState by mediaPlayerViewModel.uiState.collectAsState()
    val mediaPagerState = remember(uiState) { uiState.pagerState }

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    val isRoot = navController.previousBackStackEntry == null

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { mediaPagerState.pageCount }
    )

    AmbientAware { ambientStateUpdate ->
        val ambientState = remember(ambientStateUpdate) { ambientStateUpdate }

        val keyFunc: (Int) -> MediaPageType = remember(mediaPagerState, ambientState) {
            pagerKey@{ pageIdx ->
                if (ambientState.isAmbient)
                    return@pagerKey MediaPageType.Player

                if (pageIdx == 1) {
                    if (mediaPagerState.supportsCustomActions) {
                        return@pagerKey MediaPageType.CustomControls
                    }
                    if (mediaPagerState.supportsQueue) {
                        return@pagerKey MediaPageType.Queue
                    }
                    if (mediaPagerState.supportsBrowser) {
                        return@pagerKey MediaPageType.Browser
                    }
                } else if (pageIdx == 2) {
                    if (mediaPagerState.supportsQueue) {
                        return@pagerKey MediaPageType.Queue
                    }
                    if (mediaPagerState.supportsBrowser) {
                        return@pagerKey MediaPageType.Browser
                    }
                } else if (pageIdx == 3) {
                    return@pagerKey MediaPageType.Browser
                }

                MediaPageType.Player
            }
        }

        SwipeToDismissPagerScreen(
            modifier = modifier,
            isRoot = isRoot,
            swipeToDismissBoxState = swipeToDismissBoxState,
            state = pagerState,
            hidePagerIndicator = ambientState.isAmbient || uiState.isLoading || !uiState.isPlayerAvailable,
            timeText = {
                if (pagerState.currentPage == 0) {
                    TimeText()
                }
            },
            pagerKey = keyFunc
        ) { pageIdx ->
            val key = keyFunc(pageIdx)

            when (key) {
                MediaPageType.Player -> {
                    MediaPlayerControlsPage(
                        mediaPlayerViewModel = mediaPlayerViewModel,
                        volumeViewModel = volumeViewModel,
                        navController = navController,
                        ambientState = ambientState
                    )
                }

                MediaPageType.CustomControls -> {
                    MediaCustomControlsPage(
                        mediaPlayerViewModel = mediaPlayerViewModel
                    )
                }

                MediaPageType.Browser -> {
                    MediaBrowserPage(
                        mediaPlayerViewModel = mediaPlayerViewModel
                    )
                }

                MediaPageType.Queue -> {
                    MediaQueuePage(
                        mediaPlayerViewModel = mediaPlayerViewModel
                    )
                }
            }

            LaunchedEffect(pagerState, pagerState.targetPage, pagerState.currentPage) {
                val targetPageKey = keyFunc(pagerState.targetPage)
                if (mediaPagerState.currentPageKey != targetPageKey) {
                    mediaPlayerViewModel.updateCurrentPage(targetPageKey)
                }
            }
        }

        ConfirmationOverlay(
            confirmationData = confirmationData,
            onTimeout = { confirmationViewModel.clearFlow() },
            showDialog = ambientState.isInteractive && confirmationData != null
        )
    }

    LaunchedEffect(context) {
        mediaPlayerViewModel.initActivityContext(activity)
    }

    LaunchedEffect(app, autoLaunch) {
        if (autoLaunch) {
            mediaPlayerViewModel.autoLaunch()
            return@LaunchedEffect
        }

        if (app != null) {
            mediaPlayerViewModel.updateMediaPlayerDetails(app)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            mediaPlayerViewModel.eventFlow.collect { event ->
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
                                mediaPlayerViewModel.openPlayStore(activity)

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

                    MediaHelper.MediaPlayerConnectPath,
                    MediaHelper.MediaPlayerAutoLaunchPath -> {
                        val actionStatus =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (actionStatus == ActionStatus.PERMISSION_DENIED) {
                            confirmationViewModel.showConfirmation(
                                ConfirmationData(
                                    title = context.getString(R.string.error_permissiondenied)
                                )
                            )

                            mediaPlayerViewModel.openAppOnPhone(activity, false)
                        }
                    }

                    MediaHelper.MediaPlayPath -> {
                        val actionStatus =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (actionStatus == ActionStatus.TIMEOUT) {
                            confirmationViewModel.showConfirmation(
                                ConfirmationData(
                                    title = context.getString(R.string.error_playback_failed)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Update statuses
        mediaPlayerViewModel.refreshStatus()
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerViewModel.requestPlayerDisconnect()
        }
    }
}

@Composable
private fun MediaPlayerControlsPage(
    mediaPlayerViewModel: MediaPlayerViewModel,
    volumeViewModel: VolumeViewModel,
    navController: NavController,
    ambientState: AmbientState,
    focusRequester: FocusRequester = rememberFocusRequester()
) {
    val context = LocalContext.current

    val uiState by mediaPlayerViewModel.uiState.collectAsState()
    val playerState by mediaPlayerViewModel.playerState.collectAsState()
    val playbackStateEvent by mediaPlayerViewModel.playbackStateEvent.collectAsState()

    val playerUiController =
        remember(mediaPlayerViewModel) { MediaPlayerUiController(mediaPlayerViewModel) }
    val volumeUiState by volumeViewModel.volumeUiState.collectAsState()

    MediaPlayerControlsPage(
        modifier = Modifier
            .ambientMode(ambientState)
            .rotaryScrollable(
                focusRequester = focusRequester,
                behavior = volumeRotaryBehavior(
                    volumeUiStateProvider = { volumeUiState },
                    onRotaryVolumeInput = { newVolume -> volumeViewModel.setVolume(newVolume) }
                )
            ),
        uiState = uiState,
        playerState = playerState,
        playbackStateEvent = playbackStateEvent,
        volumeUiState = volumeUiState,
        ambientState = ambientState,
        onRefresh = {
            mediaPlayerViewModel.refreshStatus()
        },
        onOpenPlayerList = {
            navController.navigate(
                Screen.MediaPlayerList.route,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(Screen.MediaPlayerList.route, true)
                    .build()
            )
        },
        onVolumeUp = {
            volumeViewModel.increaseVolume()
        },
        onVolumeDown = {
            volumeViewModel.decreaseVolume()
        },
        playerUiController = playerUiController,
        displayVolumeIndicatorEvents = volumeViewModel.displayIndicatorEvents,
        focusRequester = focusRequester
    )

    LaunchedEffect(context) {
        mediaPlayerViewModel.refreshPlayerState()
    }
}

@Composable
private fun MediaPlayerControlsPage(
    modifier: Modifier = Modifier,
    uiState: MediaPlayerUiState,
    playerState: PlayerState = uiState.playerState,
    playbackStateEvent: PlaybackStateEvent = uiState.playerState.toPlaybackStateEvent(),
    volumeUiState: VolumeUiState = VolumeUiState(),
    ambientState: AmbientState = AmbientState.Interactive,
    onRefresh: () -> Unit = {},
    onOpenPlayerList: () -> Unit = {},
    onVolumeUp: () -> Unit = {},
    onVolumeDown: () -> Unit = {},
    playerUiController: PlayerUiController = NoopPlayerUiController(),
    displayVolumeIndicatorEvents: Flow<Unit> = emptyFlow(),
    focusRequester: FocusRequester = rememberFocusRequester()
) {
    val isAmbient = remember(ambientState) { ambientState.isAmbient }

    // Progress
    val timestampProvider = remember { TimestampProvider { System.currentTimeMillis() } }

    LoadingContent(
        empty = !uiState.isPlayerAvailable && !isAmbient,
        emptyContent = {
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
                        text = stringResource(id = R.string.error_nomusicplayers),
                        textAlign = TextAlign.Center
                    )
                    CompactChip(
                        label = {
                            Text(text = stringResource(id = R.string.action_retry))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
                                contentDescription = stringResource(id = R.string.action_retry)
                            )
                        },
                        onClick = onRefresh
                    )
                }
            }
        },
        loading = uiState.isLoading && !isAmbient
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            PlayerScreen(
                modifier = modifier,
                mediaDisplay = {
                    if (uiState.isPlaybackLoading && !isAmbient) {
                        LoadingMediaDisplay()
                    } else if (!playerState.isEmpty()) {
                        if (!isAmbient) {
                            MarqueeTextMediaDisplay(
                                title = playerState.title,
                                artist = playerState.artist
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = playerState.title.orEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .padding(top = 2.dp, bottom = .8.dp),
                                    color = MaterialTheme.colors.onBackground,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.button,
                                )
                                Text(
                                    text = playerState.artist.orEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .padding(top = 2.dp, bottom = .6.dp),
                                    color = MaterialTheme.colors.onBackground,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.body2,
                                )
                            }
                        }
                    } else {
                        NothingPlayingDisplay()
                    }
                },
                controlButtons = {
                    if (!isAmbient) {
                        CompositionLocalProvider(LocalTimestampProvider provides timestampProvider) {
                            AnimatedMediaControlButtons(
                                onPlayButtonClick = {
                                    playerUiController.play()
                                },
                                onPauseButtonClick = {
                                    playerUiController.pause()
                                },
                                playPauseButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                                playing = playerState.playbackState == PlaybackState.PLAYING,
                                onSeekToPreviousButtonClick = {
                                    playerUiController.skipToPreviousMedia()
                                },
                                seekToPreviousButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                                onSeekToNextButtonClick = {
                                    playerUiController.skipToNextMedia()
                                },
                                seekToNextButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                                trackPositionUiModel = TrackPositionUiModelMapper.map(
                                    playbackStateEvent
                                )
                            )
                        }
                    } else {
                        ControlButtonLayout(
                            leftButton = {},
                            middleButton = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (playerState.playbackState == PlaybackState.PLAYING) {
                                        MediaButton(
                                            onClick = {},
                                            icon = ImageVector.vectorResource(id = R.drawable.ic_outline_pause_24),
                                            contentDescription = stringResource(id = R.string.horologist_pause_button_content_description)
                                        )
                                    } else {
                                        MediaButton(
                                            onClick = {},
                                            icon = ImageVector.vectorResource(id = R.drawable.ic_outline_play_arrow_24),
                                            contentDescription = stringResource(id = R.string.horologist_play_button_content_description)
                                        )
                                    }
                                }
                            },
                            rightButton = {}
                        )
                    }
                },
                buttons = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isAmbient) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                SettingsButton(
                                    onClick = onVolumeDown,
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_baseline_volume_down_24),
                                    contentDescription = stringResource(R.string.horologist_volume_screen_volume_down_content_description),
                                    tapTargetSize = ButtonDefaults.ExtraSmallButtonSize
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                uiState.mediaPlayerDetails.bitmapIcon?.let {
                                    Image(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable(onClick = onOpenPlayerList),
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = stringResource(R.string.desc_open_player_list)
                                    )
                                } ?: run {
                                    Image(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable(onClick = onOpenPlayerList),
                                        painter = painterResource(R.drawable.ic_play_circle_filled_white_24dp),
                                        contentDescription = stringResource(R.string.desc_open_player_list)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                SettingsButton(
                                    onClick = onVolumeUp,
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_volume_up_white_24dp),
                                    contentDescription = stringResource(R.string.horologist_volume_screen_volume_up_content_description),
                                    tapTargetSize = ButtonDefaults.ExtraSmallButtonSize
                                )
                            }
                        }
                    }
                },
                background = {
                    playerState.artworkBitmap?.takeUnless { isAmbient }?.let {
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            bitmap = it.asImageBitmap(),
                            colorFilter = ColorFilter.tint(
                                Color.Black.copy(alpha = 0.66f),
                                BlendMode.SrcAtop
                            ),
                            contentDescription = stringResource(R.string.desc_artwork)
                        )
                    }
                }
            )

            VolumePositionIndicator(
                volumeUiState = { volumeUiState },
                displayIndicatorEvents = displayVolumeIndicatorEvents
            )

            LaunchedEffect(uiState, uiState.pagerState) {
                if (uiState.pagerState.currentPageKey == MediaPageType.Player) {
                    delay(500)
                    focusRequester.requestFocus()
                }
            }
        }
    }
}

@Composable
private fun MediaCustomControlsPage(
    mediaPlayerViewModel: MediaPlayerViewModel
) {
    val context = LocalContext.current

    val uiState by mediaPlayerViewModel.uiState.collectAsState()

    MediaCustomControlsPage(
        uiState = uiState,
        onItemClick = { item ->
            mediaPlayerViewModel.requestCustomMediaActionItem(item.id)
        }
    )

    LaunchedEffect(context) {
        mediaPlayerViewModel.requestUpdateCustomControls()
    }
}

@Composable
private fun MediaCustomControlsPage(
    uiState: MediaPlayerUiState,
    focusRequester: FocusRequester = rememberFocusRequester(),
    onItemClick: (MediaItemModel) -> Unit = {}
) {
    LoadingContent(
        empty = false,
        emptyContent = {},
        loading = uiState.isLoading
    ) {
        val scrollState = rememberResponsiveColumnState(
            contentPadding = ScalingLazyColumnDefaults.padding(
                first = ScalingLazyColumnDefaults.ItemType.Chip,
                last = ScalingLazyColumnDefaults.ItemType.Chip
            )
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            TimeText(Modifier.scrollAway { scrollState })

            ScalingLazyColumn(
                scrollState = scrollState,
                focusRequester = focusRequester
            ) {
                items(uiState.mediaCustomItems) { item ->
                    Chip(
                        label = item.title ?: "",
                        icon = {
                            item.icon?.let { bmp ->
                                Icon(
                                    modifier = Modifier.size(ChipDefaults.IconSize),
                                    bitmap = bmp.asImageBitmap(),
                                    tint = Color.White,
                                    contentDescription = item.title
                                )
                            }
                        },
                        onClick = {
                            onItemClick(item)
                        },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            LaunchedEffect(Unit) {
                scrollState.state.scrollToItem(0)
            }

            LaunchedEffect(uiState, uiState.pagerState) {
                if (uiState.pagerState.currentPageKey == MediaPageType.CustomControls) {
                    delay(500)
                    focusRequester.requestFocus()
                }
            }
        }
    }
}

@Composable
private fun MediaBrowserPage(
    mediaPlayerViewModel: MediaPlayerViewModel
) {
    val context = LocalContext.current
    val uiState by mediaPlayerViewModel.uiState.collectAsState()

    MediaBrowserPage(
        uiState = uiState,
        onItemClick = { item ->
            mediaPlayerViewModel.requestBrowserActionItem(item.id)
        }
    )

    LaunchedEffect(context) {
        mediaPlayerViewModel.requestUpdateBrowserItems()
    }
}

@Composable
private fun MediaBrowserPage(
    uiState: MediaPlayerUiState,
    focusRequester: FocusRequester = rememberFocusRequester(),
    onItemClick: (MediaItemModel) -> Unit = {}
) {
    LoadingContent(
        empty = false,
        emptyContent = {},
        loading = uiState.isLoading
    ) {
        val scrollState = rememberResponsiveColumnState(
            contentPadding = ScalingLazyColumnDefaults.padding(
                first = ScalingLazyColumnDefaults.ItemType.Chip,
                last = ScalingLazyColumnDefaults.ItemType.Chip
            )
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            TimeText(Modifier.scrollAway { scrollState })

            ScalingLazyColumn(
                scrollState = scrollState,
                focusRequester = focusRequester
            ) {
                items(uiState.mediaBrowserItems) { item ->
                    Chip(
                        label = if (item.id == MediaHelper.ACTIONITEM_BACK) {
                            stringResource(id = R.string.label_back)
                        } else {
                            item.title ?: ""
                        },
                        icon = {
                            if (item.id == MediaHelper.ACTIONITEM_BACK) {
                                Icon(
                                    modifier = Modifier.size(ChipDefaults.IconSize),
                                    painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                                    tint = Color.White,
                                    contentDescription = stringResource(id = R.string.label_back)
                                )
                            } else {
                                item.icon?.let { bmp ->
                                    Icon(
                                        modifier = Modifier.size(ChipDefaults.IconSize),
                                        bitmap = bmp.asImageBitmap(),
                                        tint = Color.White,
                                        contentDescription = item.title
                                    )
                                }
                            }
                        },
                        onClick = {
                            onItemClick(item)
                        },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            LaunchedEffect(Unit) {
                scrollState.state.scrollToItem(0)
            }

            LaunchedEffect(uiState, uiState.pagerState) {
                if (uiState.pagerState.currentPageKey == MediaPageType.Browser) {
                    delay(500)
                    focusRequester.requestFocus()
                }
            }
        }
    }
}

@Composable
private fun MediaQueuePage(
    mediaPlayerViewModel: MediaPlayerViewModel
) {
    val context = LocalContext.current
    val uiState by mediaPlayerViewModel.uiState.collectAsState()

    MediaQueuePage(
        uiState = uiState,
        onItemClick = { item ->
            mediaPlayerViewModel.requestQueueActionItem(item.id)
        }
    )

    LaunchedEffect(context) {
        mediaPlayerViewModel.requestUpdateQueueItems()
    }
}

@Composable
private fun MediaQueuePage(
    uiState: MediaPlayerUiState,
    focusRequester: FocusRequester = rememberFocusRequester(),
    onItemClick: (MediaItemModel) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LoadingContent(
        empty = false,
        emptyContent = {},
        loading = uiState.isLoading
    ) {
        val scrollState = rememberResponsiveColumnState(
            contentPadding = ScalingLazyColumnDefaults.padding(
                first = ScalingLazyColumnDefaults.ItemType.Chip,
                last = ScalingLazyColumnDefaults.ItemType.Chip
            )
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            TimeText(Modifier.scrollAway { scrollState })

            ScalingLazyColumn(
                scrollState = scrollState,
                focusRequester = focusRequester
            ) {
                items(uiState.mediaQueueItems) { item ->
                    Chip(
                        label = item.title ?: "",
                        icon = {
                            item.icon?.let { bmp ->
                                Icon(
                                    modifier = Modifier.size(ChipDefaults.IconSize),
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = item.title,
                                    tint = Color.Unspecified
                                )
                            }
                        },
                        onClick = {
                            onItemClick(item)
                            lifecycleOwner.lifecycleScope.launch {
                                delay(1000)
                                scrollState.state.scrollToItem(0)
                            }
                        },
                        colors = if (item.id.toLong() == uiState.activeQueueItemId) {
                            ChipDefaults.gradientBackgroundChipColors()
                        } else {
                            ChipDefaults.secondaryChipColors()
                        }
                    )
                }
            }

            LaunchedEffect(Unit) {
                if (uiState.activeQueueItemId != -1L) {
                    uiState.mediaQueueItems.indexOfFirst {
                        it.id.toLong() == uiState.activeQueueItemId
                    }.takeIf { it > 0 }?.run {
                        scrollState.state.scrollToItem(this)
                    }
                }
            }

            LaunchedEffect(uiState, uiState.pagerState) {
                if (uiState.pagerState.currentPageKey == MediaPageType.Queue) {
                    delay(500)
                    focusRequester.requestFocus()
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewNoMediaPlayer() {
    val uiState = remember {
        MediaPlayerUiState()
    }

    MediaPlayerControlsPage(
        uiState = uiState
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewMediaControls() {
    val context = LocalContext.current

    val background = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.sample_image)?.toBitmap()
    }

    val uiState = remember {
        MediaPlayerUiState(
            isPlayerAvailable = true,
            playerState = PlayerState(
                playbackState = PlaybackState.PLAYING,
                title = "Title",
                artist = "Artist",
                artworkBitmap = background
            ),
            audioStreamState = AudioStreamState(5, 0, 10, AudioStreamType.MUSIC)
        )
    }

    MediaPlayerControlsPage(
        uiState = uiState
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewMediaControlsInAmbientMode() {
    val context = LocalContext.current

    val background = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.sample_image)?.toBitmap()
    }

    val uiState = remember {
        MediaPlayerUiState(
            isPlayerAvailable = true,
            playerState = PlayerState(
                playbackState = PlaybackState.PLAYING,
                title = "Title",
                artist = "Artist",
                artworkBitmap = background
            )
        )
    }

    MediaPlayerControlsPage(
        uiState = uiState,
        ambientState = AmbientState.Ambient(
            burnInProtectionRequired = true,
            deviceHasLowBitAmbient = true
        )
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewCustomControls() {
    val context = LocalContext.current

    val uiState = remember {
        MediaPlayerUiState(
            isPlayerAvailable = true,
            mediaCustomItems = List(5) {
                MediaItemModel(it.toString()).apply {
                    title = "Item ${it + 1}"
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_icon)!!.toBitmap()
                }
            }
        )
    }

    MediaCustomControlsPage(
        uiState = uiState
    )
}