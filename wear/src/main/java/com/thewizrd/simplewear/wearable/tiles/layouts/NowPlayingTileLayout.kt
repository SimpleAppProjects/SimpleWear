@file:kotlin.OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.wearable.tiles.layouts

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_CROP
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.DataCardStyle
import androidx.wear.protolayout.material3.TitleCardStyle
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.backgroundImage
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconEdgeButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textDataCard
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.tileRendererPreviewData
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.media.PositionState
import com.thewizrd.shared_resources.utils.ImageUtils.toByteArray
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.theme.wearTileColorScheme
import com.thewizrd.simplewear.ui.tiles.tools.WearPreviewDevices
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileMessenger.PlayerAction
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileState
import com.thewizrd.simplewear.wearable.tiles.NowPlayingTileRenderer
import com.thewizrd.simplewear.wearable.tiles.NowPlayingTileRenderer.Companion.ID_ARTWORK
import com.thewizrd.simplewear.wearable.tiles.NowPlayingTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.NowPlayingTileRenderer.Companion.ID_PHONEDISCONNECTED
import com.thewizrd.simplewear.wearable.tiles.NowPlayingTileRenderer.Companion.ID_PLAYINGICON
import kotlinx.coroutines.runBlocking

@SuppressLint("ProtoLayoutPrimaryLayoutResponsive")
@OptIn(ProtoLayoutExperimental::class)
internal fun NowPlayingTileLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: MediaPlayerTileState
): LayoutElement =
    materialScope(context, deviceParameters, defaultColorScheme = wearTileColorScheme) {
        if (state.connectionStatus != WearConnectionStatus.CONNECTED) {
            when (state.connectionStatus) {
                WearConnectionStatus.APPNOTINSTALLED -> {
                    primaryLayout(
                        titleSlot = {
                            text(text = context.getString(R.string.title_nowplaying).layoutString)
                        },
                        mainSlot = {
                            textDataCard(
                                onClick = clickable(
                                    action = NowPlayingTileRenderer.getTapAction(context)
                                ),
                                width = expand(),
                                height = expand(),
                                title = {
                                    text(
                                        text = context.getString(R.string.error_notinstalled).layoutString,
                                        typography = BODY_MEDIUM,
                                        maxLines = 3
                                    )
                                },
                                colors = filledTonalCardColors(),
                                style = DataCardStyle.smallDataCardStyle()
                            )
                        },
                        bottomSlot = {
                            iconEdgeButton(
                                modifier = LayoutModifier.contentDescription(context.getString(R.string.common_open_on_phone)),
                                onClick = clickable(id = ID_OPENONPHONE),
                                iconContent = {
                                    icon(ID_OPENONPHONE)
                                }
                            )
                        }
                    )
                }

                else -> {
                    primaryLayout(
                        titleSlot = {
                            text(text = context.getString(R.string.title_nowplaying).layoutString)
                        },
                        mainSlot = {
                            textDataCard(
                                onClick = clickable(
                                    action = NowPlayingTileRenderer.getTapAction(context)
                                ),
                                width = expand(),
                                height = expand(),
                                title = {
                                    text(
                                        text = context.getString(R.string.status_disconnected).layoutString,
                                        typography = BODY_MEDIUM,
                                        maxLines = 3
                                    )
                                },
                                colors = filledTonalCardColors(),
                                style = DataCardStyle.smallDataCardStyle()
                            )
                        },
                        bottomSlot = {
                            iconEdgeButton(
                                modifier = LayoutModifier.contentDescription(context.getString(R.string.status_disconnected)),
                                onClick = clickable(id = ID_PHONEDISCONNECTED),
                                iconContent = {
                                    icon(ID_PHONEDISCONNECTED)
                                }
                            )
                        }
                    )
                }
            }
        } else if (state.isEmpty || state.playbackState == null || state.playbackState == PlaybackState.NONE) {
            primaryLayout(
                titleSlot = {
                    text(text = context.getString(R.string.title_nowplaying).layoutString)
                },
                mainSlot = {
                    textDataCard(
                        onClick = clickable(
                            action = NowPlayingTileRenderer.getTapAction(context)
                        ),
                        width = expand(),
                        height = expand(),
                        title = {
                            text(
                                text = context.getString(R.string.message_playback_stopped).layoutString,
                                typography = BODY_MEDIUM,
                                maxLines = 3
                            )
                        },
                        colors = filledTonalCardColors(),
                        style = DataCardStyle.smallDataCardStyle()
                    )
                },
                bottomSlot = {
                    textEdgeButton(
                        onClick = clickable(id = PlayerAction.PLAY.name),
                        labelContent = {
                            text(context.getString(R.string.action_play).layoutString)
                        }
                    )
                }
            )
        } else {
            primaryLayout(
                titleSlot = {
                    text(text = context.getString(R.string.title_nowplaying).layoutString)
                },
                mainSlot = {
                    titleCard(
                        onClick = clickable(NowPlayingTileRenderer.getTapAction(context)),
                        height = expand(),
                        backgroundContent = {
                            backgroundImage(
                                protoLayoutResourceId = ID_ARTWORK,
                                width = expand(),
                                height = expand(),
                                contentScaleMode = CONTENT_SCALE_MODE_CROP
                            )
                        },
                        title = {
                            text(
                                text = (state.title ?: "").layoutString,
                                color = colorScheme.onSurface
                            )
                        },
                        content = state.artist?.let {
                            {
                                text(
                                    text = it.layoutString,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        style = TitleCardStyle.largeTitleCardStyle()
                    )
                },
                bottomSlot = {
                    if (state.playbackState == PlaybackState.PLAYING) {
                        iconEdgeButton(
                            onClick = clickable(id = PlayerAction.PAUSE.name),
                            iconContent = {
                                icon(protoLayoutResourceId = ID_PLAYINGICON)
                            }
                        )
                    } else {
                        textEdgeButton(
                            onClick = clickable(id = PlayerAction.PLAY.name),
                            labelContent = {
                                text(context.getString(R.string.action_play).layoutString)
                            }
                        )
                    }
                }
            )
        }
    }

@WearPreviewDevices
private fun NowPlayingPausedTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        title = "Title",
        artist = "Artist",
        playbackState = PlaybackState.PAUSED,
        audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
        positionState = PositionState(100, 50),
        artwork = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.sample_image)?.toBitmapOrNull()
                ?.toByteArray()
        },
        appIcon = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ic_play_circle_simpleblue)
                ?.toBitmapOrNull()
                ?.toByteArray()
        }
    )

    return tileRendererPreviewData(
        renderer = NowPlayingTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state
    )
}

@WearPreviewDevices
private fun NowPlayingTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        title = "Title",
        artist = "Artist",
        playbackState = PlaybackState.PLAYING,
        audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
        positionState = PositionState(100, 50),
        artwork = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.sample_image)?.toBitmapOrNull()
                ?.toByteArray()
        },
        appIcon = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ic_play_circle_simpleblue)
                ?.toBitmapOrNull()
                ?.toByteArray()
        }
    )

    return tileRendererPreviewData(
        renderer = NowPlayingTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state
    )
}

@WearPreviewDevices
private fun NowPlayingEmptyTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.DISCONNECTED,
        title = null,
        artist = null,
        playbackState = null,
        audioStreamState = null,
        artwork = null
    )

    return tileRendererPreviewData(
        renderer = NowPlayingTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state
    )
}

@WearPreviewDevices
private fun NotPlayingTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        title = null,
        artist = null,
        playbackState = PlaybackState.NONE,
        audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
        artwork = null,
        appIcon = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ic_play_circle_simpleblue)
                ?.toBitmapOrNull()
                ?.toByteArray()
        }
    )

    return tileRendererPreviewData(
        renderer = NowPlayingTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state,
    )
}