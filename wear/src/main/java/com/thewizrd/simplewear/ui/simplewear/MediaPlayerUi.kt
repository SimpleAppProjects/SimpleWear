@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.PageIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.audio.ui.material3.VolumeLevelIndicator
import com.google.android.horologist.audio.ui.material3.components.actions.SettingsButton
import com.google.android.horologist.audio.ui.material3.components.actions.SettingsButtonDefaults
import com.google.android.horologist.audio.ui.material3.volumeRotaryBehavior
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.google.android.horologist.images.base.paintable.BitmapPaintable.Companion.asPaintable
import com.google.android.horologist.media.model.PlaybackStateEvent
import com.google.android.horologist.media.model.TimestampProvider
import com.google.android.horologist.media.ui.material3.components.ButtonGroupLayoutDefaults
import com.google.android.horologist.media.ui.material3.components.ambient.AmbientMediaControlButtons
import com.google.android.horologist.media.ui.material3.components.ambient.AmbientMediaInfoDisplay
import com.google.android.horologist.media.ui.material3.components.ambient.AmbientSeekToNextButton
import com.google.android.horologist.media.ui.material3.components.ambient.AmbientSeekToPreviousButton
import com.google.android.horologist.media.ui.material3.components.animated.AnimatedMediaControlButtons
import com.google.android.horologist.media.ui.material3.components.animated.AnimatedMediaInfoDisplay
import com.google.android.horologist.media.ui.material3.components.background.ArtworkImageBackground
import com.google.android.horologist.media.ui.material3.components.display.TextMediaDisplay
import com.google.android.horologist.media.ui.material3.screens.player.PlayerScreen
import com.google.android.horologist.media.ui.state.LocalTimestampProvider
import com.google.android.horologist.media.ui.state.mapper.TrackPositionUiModelMapper
import com.google.android.horologist.media.ui.state.model.MediaUiModel
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.JSONParser
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
import com.thewizrd.simplewear.ui.components.HorizontalPagerScreen
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MediaPlayerUi(
    modifier: Modifier = Modifier,
    navController: NavController,
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

        HorizontalPagerScreen(
            modifier = modifier,
            pagerState = pagerState,
            hidePagerIndicator = ambientState.isAmbient || uiState.isLoading || !uiState.isPlayerAvailable,
            pagerKey = keyFunc,
            pagerIndicatorBackgroundColor = PageIndicatorDefaults.backgroundColor.copy(alpha = 0.5f)
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
                                mediaPlayerViewModel.openPlayStore()

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
                            confirmationViewModel.showOpenOnPhoneForFailure(
                                message = context.getString(
                                    R.string.error_permissiondenied
                                )
                            )

                            mediaPlayerViewModel.openAppOnPhone(false)
                        }
                    }

                    MediaHelper.MediaPlayPath -> {
                        val actionStatus =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (actionStatus == ActionStatus.TIMEOUT) {
                            confirmationViewModel.showFailure(message = context.getString(R.string.error_playback_failed))
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

    ScreenScaffold(
        modifier = Modifier.fillMaxSize(),
        scrollIndicator = {
            VolumeLevelIndicator(
                volumeUiState = { volumeUiState },
                displayIndicatorEvents = displayVolumeIndicatorEvents
            )
        }
    ) { contentPadding ->
        LoadingContent(
            empty = !uiState.isPlayerAvailable && !isAmbient,
            emptyContent = {
                Box(
                    modifier = Modifier
                        .padding(contentPadding)
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
                        CompactButton(
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
                        val mediaUiModel = remember(playerState, uiState.isPlaybackLoading) {
                            if (!playerState.isEmpty() && !uiState.isPlaybackLoading) {
                                MediaUiModel.Ready(
                                    id = "",
                                    title = playerState.title ?: "",
                                    subtitle = playerState.artist ?: ""
                                )
                            } else {
                                MediaUiModel.Loading
                            }
                        }

                        if (!isAmbient) {
                            AnimatedMediaInfoDisplay(
                                media = mediaUiModel,
                                loading = uiState.isPlaybackLoading
                            )
                        } else {
                            AmbientMediaInfoDisplay(
                                media = mediaUiModel,
                                loading = uiState.isPlaybackLoading
                            )
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
                            val leftButtonPadding =
                                ButtonGroupLayoutDefaults.getSideButtonsPadding(isLeftButton = true)
                            val rightButtonPadding =
                                ButtonGroupLayoutDefaults.getSideButtonsPadding(isLeftButton = false)

                            AmbientMediaControlButtons(
                                onPlayButtonClick = {
                                    playerUiController.play()
                                },
                                onPauseButtonClick = {
                                    playerUiController.pause()
                                },
                                playPauseButtonEnabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                                playing = playerState.playbackState == PlaybackState.PLAYING,
                                leftButton = {
                                    AmbientSeekToPreviousButton(
                                        onClick = {
                                            playerUiController.skipToPreviousMedia()
                                        },
                                        buttonPadding = leftButtonPadding,
                                        enabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                                    )
                                },
                                rightButton = {
                                    AmbientSeekToNextButton(
                                        onClick = {
                                            playerUiController.skipToNextMedia()
                                        },
                                        buttonPadding = rightButtonPadding,
                                        enabled = !uiState.isPlaybackLoading || playerState.playbackState > PlaybackState.LOADING,
                                    )
                                }
                            )
                        }
                    },
                    buttons = {
                        SettingsButtonsLayout(
                            modifier = Modifier.fillMaxSize(),
                            isAmbient = isAmbient,
                            leftButton = SettingsButtonData(
                                onClick = onVolumeDown,
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_baseline_volume_down_24),
                                contentDescription = stringResource(R.string.horologist_volume_screen_volume_down_content_description)
                            ),
                            brandImage = BrandImageData(
                                bitmap = uiState.mediaPlayerDetails.bitmapIcon,
                                onClick = onOpenPlayerList
                            ),
                            rightButton = SettingsButtonData(
                                onClick = onVolumeUp,
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_volume_up_white_24dp),
                                contentDescription = stringResource(R.string.horologist_volume_screen_volume_up_content_description)
                            )
                        )
                    },
                    background = {
                        ArtworkImageBackground(
                            artwork = playerState.artworkBitmap?.takeUnless { isAmbient }
                                ?.asPaintable()
                        )
                    }
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
}

private data class SettingsButtonData(
    val imageVector: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

private data class BrandImageData(
    val imageVector: ImageVector? = null,
    val painter: Painter? = null,
    val bitmap: Bitmap? = null,
    val onClick: () -> Unit
)

@Composable
private fun SettingsButtonsLayout(
    modifier: Modifier = Modifier,
    isAmbient: Boolean,
    leftButton: SettingsButtonData,
    brandImage: BrandImageData,
    rightButton: SettingsButtonData,
) {
    val isRound = LocalConfiguration.current.isScreenRound

    if (isRound) {
        RoundSettingsButtonsLayout(
            modifier = modifier,
            isAmbient = isAmbient,
            leftButton = leftButton,
            brandImage = brandImage,
            rightButton = rightButton
        )
    } else {
        SimpleSettingsButtonsLayout(
            modifier = modifier,
            isAmbient = isAmbient,
            leftButton = leftButton,
            brandImage = brandImage,
            rightButton = rightButton
        )
    }
}

@Composable
private fun SimpleSettingsButtonsLayout(
    modifier: Modifier = Modifier,
    isAmbient: Boolean,
    leftButton: SettingsButtonData,
    brandImage: BrandImageData,
    rightButton: SettingsButtonData,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.fillMaxWidth(0.11f))
            SettingsButton(
                modifier = Modifier.weight(1f),
                onClick = leftButton.onClick,
                imageVector = leftButton.imageVector,
                contentDescription = leftButton.contentDescription,
                iconSize = ButtonDefaults.ExtraSmallIconSize,
                buttonColors = if (!isAmbient) {
                    SettingsButtonDefaults.buttonColors()
                } else {
                    SettingsButtonDefaults.ambientButtonColors()
                },
                border = if (!isAmbient) {
                    null
                } else {
                    SettingsButtonDefaults.ambientButtonBorder(true)
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = brandImage.onClick),
                contentAlignment = Alignment.Center
            ) {
                BrandImage(brandImage)
            }
            SettingsButton(
                modifier = Modifier.weight(1f),
                onClick = rightButton.onClick,
                imageVector = rightButton.imageVector,
                contentDescription = rightButton.contentDescription,
                iconSize = ButtonDefaults.ExtraSmallIconSize,
                buttonColors = if (!isAmbient) {
                    SettingsButtonDefaults.buttonColors()
                } else {
                    SettingsButtonDefaults.ambientButtonColors()
                },
                border = if (!isAmbient) {
                    null
                } else {
                    SettingsButtonDefaults.ambientButtonBorder(true)
                }
            )
            Spacer(modifier = Modifier.fillMaxWidth(0.11f))
        }
    }
}

@Composable
private fun RoundSettingsButtonsLayout(
    modifier: Modifier = Modifier,
    isAmbient: Boolean,
    leftButton: SettingsButtonData,
    brandImage: BrandImageData,
    rightButton: SettingsButtonData,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val isLargeWidth = LocalConfiguration.current.screenWidthDp >= 225
    val horizontalSpacerFraction = if (isLargeWidth) 0.11f else 0.145f

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(bottom = (maxHeight * 0.012f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.fillMaxWidth(horizontalSpacerFraction))
            if (isLargeWidth) {
                SettingsButton(
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.TopCenter,
                    onClick = leftButton.onClick,
                    imageVector = leftButton.imageVector,
                    contentDescription = leftButton.contentDescription,
                    iconSize = ButtonDefaults.ExtraSmallIconSize,
                    buttonColors = if (!isAmbient) {
                        SettingsButtonDefaults.buttonColors()
                    } else {
                        SettingsButtonDefaults.ambientButtonColors()
                    },
                    border = if (!isAmbient) {
                        null
                    } else {
                        SettingsButtonDefaults.ambientButtonBorder(true)
                    }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(
                        bottom = if (isLargeWidth) {
                            (screenHeightDp * 0.03f).dp
                        } else {
                            0.dp
                        }
                    )
                    .clickable(
                        onClick = brandImage.onClick,
                        indication = null,
                        interactionSource = null,
                        role = Role.Button
                    ),
                contentAlignment = if (isLargeWidth) {
                    Alignment.BottomCenter
                } else {
                    Alignment.TopCenter
                }
            ) {
                BrandImage(brandImage)
            }
            SettingsButton(
                modifier = Modifier.weight(1f),
                alignment = Alignment.TopCenter,
                onClick = rightButton.onClick,
                imageVector = rightButton.imageVector,
                contentDescription = rightButton.contentDescription,
                iconSize = ButtonDefaults.ExtraSmallIconSize,
                buttonColors = if (!isAmbient) {
                    SettingsButtonDefaults.buttonColors()
                } else {
                    SettingsButtonDefaults.ambientButtonColors()
                },
                border = if (!isAmbient) {
                    null
                } else {
                    SettingsButtonDefaults.ambientButtonBorder(true)
                }
            )
            Spacer(modifier = Modifier.fillMaxWidth(horizontalSpacerFraction))
        }
    }
}

@Composable
private fun BrandImage(data: BrandImageData) {
    if (data.imageVector != null) {
        Image(
            modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.LargeIconSize),
            imageVector = data.imageVector,
            contentDescription = stringResource(R.string.desc_open_player_list)
        )
    } else if (data.painter != null) {
        Image(
            modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.LargeIconSize),
            painter = data.painter,
            contentDescription = stringResource(R.string.desc_open_player_list)
        )
    } else if (data.bitmap != null) {
        Image(
            modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.LargeIconSize),
            bitmap = data.bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.desc_open_player_list)
        )
    } else {
        Image(
            modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.LargeIconSize),
            painter = painterResource(R.drawable.ic_play_circle_filled_white_24dp),
            contentDescription = stringResource(R.string.desc_open_player_list)
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
        val columnState = rememberTransformingLazyColumnState()
        val contentPadding = rememberResponsiveColumnPadding(
            first = ColumnItemType.ListHeader,
            last = ColumnItemType.Button,
        )
        val transformationSpec = rememberTransformationSpec()

        ScreenScaffold(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) { contentPadding ->
            TransformingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusable()
                    .focusRequester(focusRequester),
                state = columnState,
                contentPadding = contentPadding
            ) {
                item {
                    ListHeader(
                        contentPadding = PaddingValues(
                            start = ListHeaderDefaults.ContentPadding.calculateStartPadding(
                                LocalLayoutDirection.current
                            ),
                            top = 0.dp,
                            end = ListHeaderDefaults.ContentPadding.calculateEndPadding(
                                LocalLayoutDirection.current
                            ),
                            bottom = ListHeaderDefaults.ContentPadding.calculateBottomPadding()
                        )
                    ) {
                        TextMediaDisplay(
                            title = uiState.playerState.title ?: "",
                            subtitle = uiState.playerState.artist ?: "",
                            titleIcon = uiState.mediaPlayerDetails.bitmapIcon?.asPaintable()
                        )
                    }
                }

                items(uiState.mediaCustomItems) { item ->
                    FilledTonalButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = {
                            Text(text = item.title ?: "")
                        },
                        secondaryLabel = item.subTitle?.let {
                            { Text(text = it) }
                        },
                        icon = {
                            item.icon?.let { bmp ->
                                Icon(
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                    bitmap = bmp.asImageBitmap(),
                                    tint = Color.White,
                                    contentDescription = item.title
                                )
                            }
                        },
                        onClick = {
                            onItemClick(item)
                        }
                    )
                }
            }

            LaunchedEffect(Unit) {
                columnState.scrollToItem(0)
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
    val columnState = rememberTransformingLazyColumnState()
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) { contentPadding ->
        LoadingContent(
            empty = false,
            emptyContent = {},
            loading = uiState.isLoading
        ) {
            TransformingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = columnState,
                contentPadding = contentPadding
            ) {
                item {
                    ListHeader(
                        contentPadding = PaddingValues(
                            start = ListHeaderDefaults.ContentPadding.calculateStartPadding(
                                LocalLayoutDirection.current
                            ),
                            top = 0.dp,
                            end = ListHeaderDefaults.ContentPadding.calculateEndPadding(
                                LocalLayoutDirection.current
                            ),
                            bottom = ListHeaderDefaults.ContentPadding.calculateBottomPadding()
                        )
                    ) {
                        TextMediaDisplay(
                            title = uiState.playerState.title ?: "",
                            subtitle = uiState.playerState.artist ?: "",
                            titleIcon = uiState.mediaPlayerDetails.bitmapIcon?.asPaintable()
                        )
                    }
                }

                items(uiState.mediaBrowserItems) { item ->
                    FilledTonalButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = {
                            Text(
                                text = if (item.id == MediaHelper.ACTIONITEM_BACK) {
                                    stringResource(id = R.string.label_back)
                                } else {
                                    item.title ?: ""
                                }
                            )
                        },
                        secondaryLabel = item.takeIf { it.id != MediaHelper.ACTIONITEM_BACK }?.subTitle?.let {
                            { Text(text = it) }
                        },
                        icon = {
                            if (item.id == MediaHelper.ACTIONITEM_BACK) {
                                Icon(
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                    painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                                    contentDescription = stringResource(id = R.string.label_back)
                                )
                            } else {
                                item.icon?.let { bmp ->
                                    Icon(
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = item.title
                                    )
                                }
                            }
                        },
                        onClick = {
                            onItemClick(item)
                        }
                    )
                }
            }

            LaunchedEffect(Unit) {
                columnState.scrollToItem(0)
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
    val context = LocalContext.current
    val activeQueueItemIndex = remember(uiState.activeQueueItemId, uiState.mediaQueueItems) {
        (uiState.activeQueueItemId.takeIf { it > -1L }?.let { activeId ->
            uiState.mediaQueueItems.indexOfFirst { it.id.toLong() == activeId }.takeIf { it > 0 }
        } ?: 0) + 1
    }

    val columnState = rememberTransformingLazyColumnState(
        initialAnchorItemIndex = 1
    )
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) { contentPadding ->
        LoadingContent(
            empty = false,
            emptyContent = {},
            loading = uiState.isLoading
        ) {
            TransformingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = columnState,
                contentPadding = contentPadding
            ) {
                item {
                    ListHeader(
                        contentPadding = PaddingValues(
                            start = ListHeaderDefaults.ContentPadding.calculateStartPadding(
                                LocalLayoutDirection.current
                            ),
                            top = 0.dp,
                            end = ListHeaderDefaults.ContentPadding.calculateEndPadding(
                                LocalLayoutDirection.current
                            ),
                            bottom = ListHeaderDefaults.ContentPadding.calculateBottomPadding()
                        )
                    ) {
                        TextMediaDisplay(
                            title = uiState.playerState.title ?: "",
                            subtitle = uiState.playerState.artist ?: "",
                            titleIcon = uiState.mediaPlayerDetails.bitmapIcon?.asPaintable()
                        )
                    }
                }

                items(uiState.mediaQueueItems) { item ->
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = {
                            Text(text = item.title ?: "")
                        },
                        secondaryLabel = item.subTitle?.let {
                            { Text(text = it) }
                        },
                        icon = item.icon?.let { bmp ->
                            {
                                Image(
                                    modifier = Modifier
                                        .size(ButtonDefaults.IconSize)
                                        .clip(RoundedCornerShape(4.dp)),
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = item.title
                                )
                            }
                        } ?: run {
                            if (item.id.toLong() == uiState.activeQueueItemId) {
                                {
                                    Icon(
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                        painter = painterResource(id = R.drawable.rounded_equalizer_24),
                                        contentDescription = item.title
                                    )
                                }
                            } else {
                                null
                            }
                        },
                        onClick = {
                            onItemClick(item)
                        },
                        colors = if (item.id.toLong() == uiState.activeQueueItemId) {
                            ButtonDefaults.filledVariantButtonColors()
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        }
                    )
                }
            }

            LaunchedEffect(uiState.activeQueueItemId, uiState.mediaQueueItems) {
                delay(500)

                if (isActive && !columnState.isScrollInProgress) {
                    columnState.animateScrollToItem(activeQueueItemIndex)
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
            playerState = PlayerState(
                playbackState = PlaybackState.PLAYING,
                title = "Title",
                artist = "Artist",
                artworkBitmap = ContextCompat.getDrawable(context, R.drawable.sample_image)!!
                    .toBitmap()
            ),
            mediaPlayerDetails = AppItemViewModel().apply {
                activityName = "Media Player"
                bitmapIcon =
                    ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)!!.toBitmap()
            },
            mediaCustomItems = List(5) {
                MediaItemModel(it.toString()).apply {
                    title = "Item ${it + 1}"
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_icon)!!.toBitmap()
                }
            },
            activeQueueItemId = 0
        )
    }

    MediaCustomControlsPage(
        uiState = uiState
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewMediaQueue() {
    val context = LocalContext.current

    val uiState = remember {
        MediaPlayerUiState(
            isPlayerAvailable = true,
            playerState = PlayerState(
                playbackState = PlaybackState.PLAYING,
                title = "Title",
                artist = "Artist",
                artworkBitmap = ContextCompat.getDrawable(context, R.drawable.sample_image)!!
                    .toBitmap()
            ),
            mediaPlayerDetails = AppItemViewModel().apply {
                activityName = "Media Player"
                bitmapIcon =
                    ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)!!.toBitmap()
            },
            mediaQueueItems = List(5) {
                MediaItemModel(it.toString()).apply {
                    title = "Item ${it + 1}"
                    icon = ContextCompat.getDrawable(context, R.drawable.sample_image)!!.toBitmap()
                }
            },
            activeQueueItemId = 0
        )
    }

    MediaQueuePage(
        uiState = uiState
    )
}