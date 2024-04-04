@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.ui.simplewear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.HierarchicalFocusCoordinator
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBox
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
import com.google.android.horologist.compose.layout.PagerScaffold
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.compose.layout.scrollAway
import com.google.android.horologist.compose.material.Chip
import com.google.android.horologist.compose.pager.HorizontalPagerDefaults
import com.google.android.horologist.media.ui.components.ControlButtonLayout
import com.google.android.horologist.media.ui.components.animated.AnimatedMediaControlButtons
import com.google.android.horologist.media.ui.components.animated.MarqueeTextMediaDisplay
import com.google.android.horologist.media.ui.components.controls.MediaButton
import com.google.android.horologist.media.ui.components.display.LoadingMediaDisplay
import com.google.android.horologist.media.ui.components.display.NothingPlayingDisplay
import com.google.android.horologist.media.ui.screens.player.PlayerScreen
import com.google.android.horologist.media.ui.state.model.TrackPositionUiModel
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.media.MediaItemModel
import com.thewizrd.simplewear.media.MediaPageType
import com.thewizrd.simplewear.media.MediaPlayerUiState
import com.thewizrd.simplewear.media.MediaPlayerViewModel
import com.thewizrd.simplewear.media.PlayerState
import com.thewizrd.simplewear.ui.ambient.ambientMode
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.theme.WearAppTheme
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalWearFoundationApi::class
)
@Composable
fun MediaPlayerUi(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val mediaPlayerViewModel = activityViewModel<MediaPlayerViewModel>()
    val uiState by mediaPlayerViewModel.uiState.collectAsState()
    val mediaPagerState = remember(uiState) { uiState.pagerState }
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { mediaPagerState.pageCount }
    )

    WearAppTheme {
        AmbientAware { ambientStateUpdate ->
            val ambientState = remember(ambientStateUpdate) { ambientStateUpdate.ambientState }

            PagerScaffold(
                modifier = Modifier.fillMaxSize(),
                timeText = {
                    if (pagerState.currentPage == 0) {
                        TimeText()
                    }
                },
                pagerState = if (ambientState != AmbientState.Interactive || uiState.isLoading || !uiState.isPlayerAvailable) null else pagerState
            ) {
                SwipeToDismissBox(
                    modifier = Modifier.background(MaterialTheme.colors.background),
                    onDismissed = {
                        activity.onBackPressed()
                    },
                    state = swipeToDismissBoxState
                ) { isBackground ->
                    if (isBackground) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.background)
                        )
                    } else {
                        val keyFunc: (Int) -> MediaPageType = remember(uiState) {
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

                        HorizontalPager(
                            modifier = modifier.edgeSwipeToDismiss(swipeToDismissBoxState),
                            state = pagerState,
                            flingBehavior = HorizontalPagerDefaults.flingParams(pagerState),
                            key = keyFunc
                        ) { pageIdx ->
                            HierarchicalFocusCoordinator(requiresFocus = { pageIdx == pagerState.currentPage }) {
                                val key = keyFunc(pageIdx)

                                when (key) {
                                    MediaPageType.Player -> {
                                        MediaPlayerControlsPage(
                                            mediaPlayerViewModel = mediaPlayerViewModel,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPlayerControlsPage(
    mediaPlayerViewModel: MediaPlayerViewModel,
    ambientState: AmbientState
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val uiState by mediaPlayerViewModel.uiState.collectAsState()
    val playerState by mediaPlayerViewModel.playerState.collectAsState()

    MediaPlayerControlsPage(
        uiState = uiState,
        playerState = playerState,
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
            mediaPlayerViewModel.showCallVolumeActivity(activity)
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
                if (uiState.isPlaybackLoading) {
                    LoadingMediaDisplay()
                } else if (!playerState.isEmpty()) {
                    MarqueeTextMediaDisplay(
                        title = playerState.title,
                        artist = playerState.artist
                    )
                } else {
                    NothingPlayingDisplay()
                }
            },
            controlButtons = {
                if (!isAmbient) {
                    AnimatedMediaControlButtons(
                        onPlayButtonClick = onPlay,
                        onPauseButtonClick = onPause,
                        playPauseButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                        playing = playerState.playbackState == PlaybackState.PLAYING,
                        onSeekToPreviousButtonClick = onSkipBack,
                        seekToPreviousButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                        onSeekToNextButtonClick = onSkipForward,
                        seekToNextButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                        trackPositionUiModel = TrackPositionUiModel.Hidden
                    )
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
                true,
                true
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