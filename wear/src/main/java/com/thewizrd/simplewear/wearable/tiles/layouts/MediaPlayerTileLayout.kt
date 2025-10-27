@file:OptIn(ProtoLayoutExperimental::class)
@file:kotlin.OptIn(ExperimentalHorologistApi::class)
@file:Suppress("FunctionName")

package com.thewizrd.simplewear.wearable.tiles.layouts

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.WrappedDimensionProp
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_MARQUEE
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_TOP
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.DataCardStyle
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.ProgressIndicatorColors
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_MEDIUM
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.circularProgressIndicator
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.iconEdgeButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textDataCard
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.modifiers.padding
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
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_APPICON
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_ARTWORK
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_PAUSE
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_PHONEDISCONNECTED
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_PLAY
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_PREVIOUS
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_SKIP
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_VOL_DOWN
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_VOL_UP
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileState
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

@SuppressLint("ProtoLayoutPrimaryLayoutResponsive")
@OptIn(ProtoLayoutExperimental::class)
internal fun MediaPlayerTileLayout(
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
                            text(text = context.getString(R.string.title_media_controller).layoutString)
                        },
                        mainSlot = {
                            textDataCard(
                                onClick = clickable(
                                    action = MediaPlayerTileRenderer.getTapAction(context)
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
                            text(text = context.getString(R.string.title_media_controller).layoutString)
                        },
                        mainSlot = {
                            textDataCard(
                                onClick = clickable(
                                    action = MediaPlayerTileRenderer.getTapAction(context)
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
                    text(text = context.getString(R.string.title_media_controller).layoutString)
                },
                mainSlot = {
                    textDataCard(
                        onClick = clickable(
                            action = MediaPlayerTileRenderer.getTapAction(context)
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
            Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .addContent(
                    Image.Builder()
                        .setResourceId(ID_ARTWORK)
                        .setWidth(expand())
                        .setHeight(expand())
                        .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
                        .build()
                )
                .addContent(
                    Box.Builder()
                        .setWidth(expand())
                        .setHeight(expand())
                        .setModifiers(
                            Modifiers.Builder()
                                .setBackground(
                                    Background.Builder()
                                        .setColor(
                                            ColorProp.Builder(0xAA000000.toInt())
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .addContent(
                            Column.Builder()
                                .setWidth(expand())
                                .setHeight(expand())
                                .addContent(
                                    Box.Builder()
                                        .setWidth(expand())
                                        .setHeight(
                                            WrappedDimensionProp.Builder()
                                                .apply {
                                                    if (deviceParameters.isLargeHeight()) {
                                                        setMinimumSize(dp(0f))
                                                    } else {
                                                        setMinimumSize(dp(68f))
                                                    }
                                                }
                                                .build()
                                        )
                                        .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                                        .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
                                        .setModifiers(
                                            Modifiers.Builder()
                                                .setPadding(
                                                    padding(
                                                        top = deviceParameters.getScreenSizeInDpFromPercentage(
                                                            if (deviceParameters.isLargeHeight()) {
                                                                13.2f
                                                            } else {
                                                                12f
                                                            }
                                                        ),
                                                        bottom = deviceParameters.getScreenSizeInDpFromPercentage(
                                                            if (deviceParameters.isLargeHeight()) {
                                                                6f
                                                            } else {
                                                                2f
                                                            }
                                                        ),
                                                    )
                                                )
                                                .build()
                                        )
                                        .addContent(
                                            Column.Builder()
                                                .setHeight(dp(38f))
                                                .apply {
                                                    if (!state.title.isNullOrBlank()) {
                                                        addContent(
                                                            Box.Builder()
                                                                .setWidth(
                                                                    dp(
                                                                        deviceParameters.getScreenWidthInDpFromPercentage(
                                                                            66.72f
                                                                        )
                                                                    )
                                                                )
                                                                .setHeight(dp(20f))
                                                                .setHorizontalAlignment(
                                                                    HORIZONTAL_ALIGN_CENTER
                                                                )
                                                                .addContent(
                                                                    text(
                                                                        text = state.title.layoutString,
                                                                        maxLines = 1,
                                                                        alignment = TEXT_ALIGN_CENTER,
                                                                        overflow = TEXT_OVERFLOW_MARQUEE,
                                                                        typography = TITLE_MEDIUM,
                                                                        color = colorScheme.onSurface
                                                                    )
                                                                )
                                                                .build()
                                                        )
                                                    }

                                                    if (!state.artist.isNullOrBlank()) {
                                                        addContent(
                                                            Box.Builder()
                                                                .setWidth(
                                                                    dp(
                                                                        deviceConfiguration.getScreenWidthInDpFromPercentage(
                                                                            if (deviceConfiguration.isLargeWidth()) {
                                                                                71f
                                                                            } else {
                                                                                75f
                                                                            }
                                                                        )
                                                                    )
                                                                )
                                                                .setHeight(dp(18f))
                                                                .setHorizontalAlignment(
                                                                    HORIZONTAL_ALIGN_CENTER
                                                                )
                                                                .addContent(
                                                                    text(
                                                                        text = state.artist.layoutString,
                                                                        maxLines = 1,
                                                                        alignment = TEXT_ALIGN_CENTER,
                                                                        overflow = TEXT_OVERFLOW_MARQUEE,
                                                                        typography = BODY_MEDIUM,
                                                                        color = colorScheme.onSurface
                                                                    )
                                                                )
                                                                .build()
                                                        )
                                                    }
                                                }
                                                .build()
                                        )
                                        .build()
                                )
                                .addContent(
                                    Box.Builder()
                                        .setWidth(expand())
                                        .setHeight(
                                            WrappedDimensionProp.Builder()
                                                .apply {
                                                    if (deviceParameters.isLargeHeight()) {
                                                        setMinimumSize(dp(80f))
                                                    } else {
                                                        setMinimumSize(dp(64f))
                                                    }
                                                }
                                                .build()
                                        )
                                        .addContent(
                                            buttonGroup(
                                                height = middleButtonSize(),
                                                width = expand(),
                                                spacing = 0f
                                            ) {
                                                buttonGroupItem {
                                                    PlayerButton(action = PlayerAction.PREVIOUS)
                                                }

                                                buttonGroupItem {
                                                    PlayPauseButton(state)
                                                }

                                                buttonGroupItem {
                                                    PlayerButton(action = PlayerAction.NEXT)
                                                }
                                            }
                                        )
                                        .build()
                                )
                                .addContent(
                                    Box.Builder()
                                        .setWidth(expand())
                                        .setHeight(weight(1f))
                                        .addContent(SettingsButtonLayout(state))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        }
    }

private fun MaterialScope.SettingsButtonLayout(
    state: MediaPlayerTileState
): LayoutElement {
    return if (deviceConfiguration.screenShape == SCREEN_SHAPE_ROUND) {
        val isLargeWidth = deviceConfiguration.isLargeWidth()
        val horizontalSpacerPercentage = if (isLargeWidth) 11f else 14.5f

        Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Row.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .setModifiers(
                        Modifiers.Builder()
                            .setPadding(
                                padding(
                                    bottom = deviceConfiguration.getScreenSizeInDpFromPercentage(
                                        1.2f
                                    )
                                )
                            )
                            .build()
                    )
                    .addContent(
                        Spacer.Builder()
                            .setWidth(
                                dp(
                                    deviceConfiguration.getScreenWidthInDpFromPercentage(
                                        horizontalSpacerPercentage
                                    )
                                )
                            )
                            .build()
                    )
                    .addContent(VolumeButton(PlayerAction.VOL_DOWN))
                    .addContent(BrandIcon(ID_APPICON))
                    .addContent(VolumeButton(PlayerAction.VOL_UP))
                    .addContent(
                        Spacer.Builder()
                            .setWidth(
                                dp(
                                    deviceConfiguration.getScreenWidthInDpFromPercentage(
                                        horizontalSpacerPercentage
                                    )
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    } else {
        Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Row.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .addContent(
                        Spacer.Builder()
                            .setWidth(dp(deviceConfiguration.getScreenWidthInDpFromPercentage(11f)))
                            .build()
                    )
                    .addContent(VolumeButton(PlayerAction.VOL_DOWN))
                    .addContent(BrandIcon(ID_APPICON))
                    .addContent(VolumeButton(PlayerAction.VOL_UP))
                    .addContent(
                        Spacer.Builder()
                            .setWidth(dp(deviceConfiguration.getScreenWidthInDpFromPercentage(11f)))
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

private fun MaterialScope.middleButtonSize(): DimensionBuilders.DpProp =
    if (deviceConfiguration.isLargeHeight()) {
        dp(80f)
    } else {
        dp(64f)
    }

private fun MaterialScope.getSideButtonsPadding(
    isLeftButton: Boolean
): Padding {
    val isLargeScreen = deviceConfiguration.isLargeHeight()

    val buttonGroupSpacingPct = if (isLargeScreen) {
        3f
    } else {
        5.2f
    }

    val adj = if (deviceConfiguration.screenShape == SCREEN_SHAPE_ROUND) {
        0f
    } else {
        if (deviceConfiguration.isLargeWidth()) 2f else 4f
    }

    return padding(
        start = max(
            if (isLeftButton) {
                deviceConfiguration.getScreenSizeInDpFromPercentage(7.1f)
            } else {
                deviceConfiguration.getScreenSizeInDpFromPercentage(buttonGroupSpacingPct)
            } - adj,
            0f
        ),
        end = max(
            if (!isLeftButton) {
                deviceConfiguration.getScreenSizeInDpFromPercentage(7.1f)
            } else {
                deviceConfiguration.getScreenSizeInDpFromPercentage(buttonGroupSpacingPct)
            } - adj,
            0f
        ),
        top = deviceConfiguration.getScreenSizeInDpFromPercentage(
            if (isLargeScreen) {
                5.2f
            } else {
                4.16f
            }
        ),
        bottom = deviceConfiguration.getScreenSizeInDpFromPercentage(
            if (isLargeScreen) {
                5.2f
            } else {
                4.16f
            }
        ),
        rtlAware = false
    )
}

private fun DeviceParameters.getScreenSizeInDpFromPercentage(
    percent: Float
): Float {
    return ceil(screenHeightDp * percent / 100f)
}

private fun DeviceParameters.getScreenWidthInDpFromPercentage(
    percent: Float
): Float {
    return ceil(screenWidthDp * percent / 100f)
}

private fun MaterialScope.PlayerButton(
    action: PlayerAction
): LayoutElement {
    val buttonPadding = getSideButtonsPadding(
        isLeftButton = action == PlayerAction.PREVIOUS
    )

    return Box.Builder()
        .setModifiers(
            Modifiers.Builder()
                .setPadding(buttonPadding)
                .build()
        )
        .setWidth(expand())
        .setHeight(expand())
        .addContent(
            iconButton(
                onClick = clickable(id = action.name),
                width = expand(),
                height = expand(),
                iconContent = {
                    icon(getResourceIdForPlayerAction(action))
                }
            )
        )
        .build()
}

private fun MaterialScope.PlayPauseButton(
    state: MediaPlayerTileState
): LayoutElement {
    val middleButtonSize = middleButtonSize()

    val action = if (state.playbackState != PlaybackState.PLAYING) {
        PlayerAction.PLAY
    } else {
        PlayerAction.PAUSE
    }

    val contentSize = if (deviceConfiguration.isLargeHeight()) {
        80f
    } else {
        64f
    }

    val playerButtonContent = Box.Builder()
        .setModifiers(
            Modifiers.Builder()
                .build()
        )
        .addContent(
            iconButton(
                onClick = clickable(id = action.name),
                width = dp(middleButtonSize.value - 14f),
                height = dp(middleButtonSize.value - 14f),
                iconContent = {
                    icon(getResourceIdForPlayerAction(action))
                }
            )
        )
        .build()

    return if (deviceConfiguration.supportsDynamicValue() && state.positionState != null) {
        val actualPercent =
            state.positionState.currentPositionMs.toFloat() / state.positionState.durationMs.toFloat()

        Box.Builder()
            .setWidth(WrappedDimensionProp.Builder().setMinimumSize(middleButtonSize).build())
            .setHeight(WrappedDimensionProp.Builder().setMinimumSize(middleButtonSize).build())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(playerButtonContent)
            .addContent(
                circularProgressIndicator(
                    staticProgress = actualPercent,
                    dynamicProgress = if (state.playbackState == PlaybackState.PLAYING) {
                        val durationFloat =
                            state.positionState.durationMs.toFloat() / 1000f

                        val positionFractional =
                            DynamicInstant.withSecondsPrecision(
                                Instant.ofEpochMilli(
                                    state.positionState.currentTimeMs
                                )
                            ).durationUntil(
                                DynamicInstant.platformTimeWithSecondsPrecision()
                            )
                                .toIntSeconds()
                                .asFloat()
                                .times(state.positionState.playbackSpeed)
                                .plus(state.positionState.currentPositionMs.toFloat() / 1000f)

                        val predictedPercent =
                            DynamicFloat.onCondition(
                                positionFractional.gt(
                                    durationFloat
                                )
                            )
                                .use(
                                    durationFloat
                                )
                                .elseUse(
                                    positionFractional
                                )
                                .div(
                                    durationFloat
                                )

                        DynamicFloat.onCondition(
                            predictedPercent.gt(
                                0f
                            )
                        )
                            .use(
                                predictedPercent
                            )
                            .elseUse(0f)
                            .animate()
                    } else {
                        null
                    },
                    startAngleDegrees = 0f,
                    endAngleDegrees = 360f,
                    strokeWidth = 4f,
                    colors = ProgressIndicatorColors(
                        colorScheme.onSecondaryContainer,
                        colorScheme.outline
                    )
                )
            )
            .build()
    } else {
        playerButtonContent
    }
}

private fun MaterialScope.VolumeButton(
    action: PlayerAction
): LayoutElement = Box.Builder()
    .setWidth(weight(1f))
    .setHeight(expand())
    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
    .setVerticalAlignment(
        if (deviceConfiguration.screenShape == SCREEN_SHAPE_ROUND) {
            VERTICAL_ALIGN_TOP
        } else {
            VERTICAL_ALIGN_CENTER
        }
    )
    .addContent(
        Box.Builder()
            .setWidth(dp(40f))
            .setHeight(dp(32f))
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(
                                ColorProp.Builder(
                                    ColorUtils.setAlphaComponent(
                                        colorScheme.onSurface.staticArgb,
                                        (0xFF * 0.24f).toInt()
                                    )
                                ).build()
                            )
                            .setCorner(
                                Corner.Builder()
                                    .setRadius(dp(22f))
                                    .build()
                            )
                            .build()
                    )
                    .setClickable(
                        Clickable.Builder()
                            .setId(action.name)
                            .setOnClick(
                                ActionBuilders.LoadAction.Builder()
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                icon(
                    protoLayoutResourceId = getResourceIdForPlayerAction(action),
                    width = dp(20f),
                    height = dp(20f),
                    tintColor = colorScheme.onSurface
                )
            )
            .build()
    )
    .build()

private fun MaterialScope.BrandIcon(
    resourceId: String
): LayoutElement = Box.Builder()
    .setWidth(weight(1f))
    .setHeight(expand())
    .apply {
        if (deviceConfiguration.screenShape == SCREEN_SHAPE_ROUND) {
            setModifiers(
                Modifiers.Builder()
                    .setPadding(padding(bottom = deviceConfiguration.screenHeightDp * 0.03f))
                    .build()
            )
            setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
        }
    }
    .addContent(
        icon(
            protoLayoutResourceId = resourceId,
            width = dp(32f),
            height = dp(32f)
        )
    )
    .build()

private fun getResourceIdForPlayerAction(action: PlayerAction): String {
    return when (action) {
        PlayerAction.PLAY -> ID_PLAY
        PlayerAction.PAUSE -> ID_PAUSE
        PlayerAction.PREVIOUS -> ID_PREVIOUS
        PlayerAction.NEXT -> ID_SKIP
        PlayerAction.VOL_UP -> ID_VOL_UP
        PlayerAction.VOL_DOWN -> ID_VOL_DOWN
    }
}

@WearPreviewDevices
private fun MediaPlayerTilePreview(context: Context): TilePreviewData {
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
        renderer = MediaPlayerTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state
    )
}

@WearPreviewDevices
private fun MediaPlayerEmptyTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.DISCONNECTED,
        title = null,
        artist = null,
        playbackState = null,
        audioStreamState = null,
        artwork = null
    )

    return tileRendererPreviewData(
        renderer = MediaPlayerTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state
    )
}

@WearPreviewDevices
private fun MediaPlayerNotPlayingTilePreview(context: Context): TilePreviewData {
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
        renderer = MediaPlayerTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state,
    )
}