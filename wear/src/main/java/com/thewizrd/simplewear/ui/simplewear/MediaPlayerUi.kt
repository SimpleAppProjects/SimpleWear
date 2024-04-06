@file:OptIn(ExperimentalHorologistApi::class, ExperimentalFoundationApi::class)

package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.components.actions.SetVolumeButton
import com.google.android.horologist.audio.ui.components.animated.AnimatedSetVolumeButton
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
import com.google.android.horologist.compose.layout.ScalingLazyColumn
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
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.media.MediaItemModel
import com.thewizrd.simplewear.media.MediaPageType
import com.thewizrd.simplewear.media.MediaPlayerUiState
import com.thewizrd.simplewear.media.MediaPlayerViewModel
import com.thewizrd.simplewear.media.PlayerState
import com.thewizrd.simplewear.media.toPlaybackStateEvent
import com.thewizrd.simplewear.ui.ambient.ambientMode
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.components.SwipeToDismissPagerScreen
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.delay
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
    val uiState by mediaPlayerViewModel.uiState.collectAsState()
    val mediaPagerState = remember(uiState) { uiState.pagerState }

    val isRoot = navController.previousBackStackEntry == null

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { mediaPagerState.pageCount }
    )

    AmbientAware { ambientStateUpdate ->
        val ambientState = remember(ambientStateUpdate) { ambientStateUpdate.ambientState }

        val keyFunc: (Int) -> MediaPageType = remember(mediaPagerState, ambientState) {
            pagerKey@{ pageIdx ->
                if (ambientState != AmbientState.Interactive)
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
            hidePagerIndicator = ambientState != AmbientState.Interactive || uiState.isLoading || !uiState.isPlayerAvailable,
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
        }
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

                            mediaPlayerViewModel.openAppOnPhone(activity, false)
                        }
                    }

                    MediaHelper.MediaPlayPath -> {
                        val actionStatus =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (actionStatus == ActionStatus.TIMEOUT) {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(R.drawable.ws_full_sad)
                                .setMessage(R.string.error_playback_failed)
                                .showOn(activity)
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
    navController: NavController,
    ambientState: AmbientState
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val uiState by mediaPlayerViewModel.uiState.collectAsState()
    val playerState by mediaPlayerViewModel.playerState.collectAsState()
    val playbackStateEvent by mediaPlayerViewModel.playbackStateEvent.collectAsState()

    MediaPlayerControlsPage(
        uiState = uiState,
        playerState = playerState,
        playbackStateEvent = playbackStateEvent,
        ambientState = ambientState,
        onRefresh = {
            mediaPlayerViewModel.refreshStatus()
        },
        onPlay = {
            mediaPlayerViewModel.requestPlayPauseAction(true)
        },
        onPause = {
            mediaPlayerViewModel.requestPlayPauseAction(false)
        },
        onSkipBack = {
            mediaPlayerViewModel.requestSkipToPreviousAction()
        },
        onSkipForward = {
            mediaPlayerViewModel.requestSkipToNextAction()
        },
        onVolume = {
            navController.navigate(
                Screen.ValueAction.getRoute(Actions.VOLUME, AudioStreamType.MUSIC)
            )
        }
    )

    LaunchedEffect(context) {
        mediaPlayerViewModel.refreshPlayerState()
    }
}

@Composable
private fun MediaPlayerControlsPage(
    uiState: MediaPlayerUiState,
    playerState: PlayerState = uiState.playerState,
    playbackStateEvent: PlaybackStateEvent = uiState.playerState.toPlaybackStateEvent(),
    ambientState: AmbientState = AmbientState.Interactive,
    onRefresh: () -> Unit = {},
    onPlay: () -> Unit = {},
    onPause: () -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onVolume: () -> Unit = {},
) {
    val volumeUiState = remember(uiState) {
        uiState.audioStreamState?.let {
            VolumeUiState(it.currentVolume, it.maxVolume, it.minVolume)
        }
    }
    val isAmbient = ambientState != AmbientState.Interactive

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
        PlayerScreen(
            modifier = Modifier.ambientMode(ambientState),
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
                            onPlayButtonClick = onPlay,
                            onPauseButtonClick = onPause,
                            playPauseButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                            playing = playerState.playbackState == PlaybackState.PLAYING,
                            onSeekToPreviousButtonClick = onSkipBack,
                            seekToPreviousButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                            onSeekToNextButtonClick = onSkipForward,
                            seekToNextButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                            trackPositionUiModel = TrackPositionUiModelMapper.map(playbackStateEvent)
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
                if (!isAmbient) {
                    if (volumeUiState != null) {
                        AnimatedSetVolumeButton(
                            onVolumeClick = onVolume,
                            volumeUiState = volumeUiState
                        )
                    } else {
                        SetVolumeButton(onVolumeClick = onVolume)
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
                        contentDescription = null
                    )
                }
            }
        )
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
        mediaPlayerViewModel.updateCustomControls()
    }
}

@Composable
private fun MediaCustomControlsPage(
    uiState: MediaPlayerUiState,
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
                columnState = scrollState
            ) {
                items(uiState.mediaCustomItems) {
                    Chip(
                        label = it.title ?: "",
                        icon = {
                            it.icon?.let { bmp ->
                                Icon(
                                    modifier = Modifier.size(ChipDefaults.IconSize),
                                    bitmap = bmp.asImageBitmap(),
                                    tint = Color.White,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = {
                            onItemClick(it)
                        },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            LaunchedEffect(Unit) {
                scrollState.state.scrollToItem(0)
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
        mediaPlayerViewModel.updateBrowserItems()
    }
}

@Composable
private fun MediaBrowserPage(
    uiState: MediaPlayerUiState,
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
                columnState = scrollState
            ) {
                items(uiState.mediaBrowserItems) {
                    Chip(
                        label = if (it.id == MediaHelper.ACTIONITEM_BACK) {
                            stringResource(id = R.string.label_back)
                        } else {
                            it.title ?: ""
                        },
                        icon = {
                            if (it.id == MediaHelper.ACTIONITEM_BACK) {
                                Icon(
                                    modifier = Modifier.size(ChipDefaults.IconSize),
                                    painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                                    tint = Color.White,
                                    contentDescription = null
                                )
                            } else {
                                it.icon?.let { bmp ->
                                    Icon(
                                        modifier = Modifier.size(ChipDefaults.IconSize),
                                        bitmap = bmp.asImageBitmap(),
                                        tint = Color.White,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        onClick = {
                            onItemClick(it)
                        },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            LaunchedEffect(Unit) {
                scrollState.state.scrollToItem(0)
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
        mediaPlayerViewModel.updateQueueItems()
    }
}

@Composable
private fun MediaQueuePage(
    uiState: MediaPlayerUiState,
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
                columnState = scrollState
            ) {
                items(uiState.mediaQueueItems) {
                    Chip(
                        label = it.title ?: "",
                        icon = {
                            it.icon?.let { bmp ->
                                Icon(
                                    modifier = Modifier.size(ChipDefaults.IconSize),
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    tint = Color.Unspecified
                                )
                            }
                        },
                        onClick = {
                            onItemClick(it)
                            lifecycleOwner.lifecycleScope.launch {
                                delay(1000)
                                scrollState.state.scrollToItem(0)
                            }
                        },
                        colors = if (it.id.toLong() == uiState.activeQueueItemId) {
                            ChipDefaults.gradientBackgroundChipColors()
                        } else {
                            ChipDefaults.secondaryChipColors()
                        }
                    )
                }
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
            )
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
            ambientDetails = AmbientLifecycleObserver.AmbientDetails(
                burnInProtectionRequired = true,
                deviceHasLowBitAmbient = true
            )
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