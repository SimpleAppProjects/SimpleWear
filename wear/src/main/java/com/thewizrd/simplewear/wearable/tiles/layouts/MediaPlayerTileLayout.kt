package com.thewizrd.simplewear.wearable.tiles.layouts

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
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
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.TypeBuilders.FloatProp
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.MultiSlotLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.ImageUtils.toByteArray
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.tools.WearTilePreviewDevices
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

private val CIRCLE_SIZE = dp(48f)
private val SMALL_CIRCLE_SIZE = dp(40f)

private val ICON_SIZE = dp(24f)
private val SMALL_ICON_SIZE = dp(20f)

private val COLORS = Colors(
    0xff91cfff.toInt(), 0xff000000.toInt(),
    0xff202124.toInt(), 0xffffffff.toInt()
)

@OptIn(ProtoLayoutExperimental::class)
internal fun MediaPlayerTileLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: MediaPlayerTileState
): LayoutElement {
    return if (state.connectionStatus != WearConnectionStatus.CONNECTED) {
        PrimaryLayout.Builder(deviceParameters)
            .apply {
                when (state.connectionStatus) {
                    WearConnectionStatus.APPNOTINSTALLED -> {
                        setContent(
                            Text.Builder(context, context.getString(R.string.error_notinstalled))
                                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                .setColor(
                                    ColorBuilders.argb(
                                        ContextCompat.getColor(context, R.color.colorSecondary)
                                    )
                                )
                                .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                .setMaxLines(3)
                                .build()
                        )

                        setPrimaryChipContent(
                            IconButton(
                                context,
                                ID_OPENONPHONE,
                                context.getString(R.string.common_open_on_phone),
                                Clickable.Builder()
                                    .setId(ID_OPENONPHONE)
                                    .setOnClick(
                                        ActionBuilders.LoadAction.Builder()
                                            .build()
                                    )
                                    .build(),
                                size = SMALL_CIRCLE_SIZE,
                                iconSize = SMALL_ICON_SIZE
                            )
                        )
                    }

                    else -> {
                        setContent(
                            Text.Builder(context, context.getString(R.string.status_disconnected))
                                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                .setColor(
                                    ColorBuilders.argb(
                                        ContextCompat.getColor(context, R.color.colorSecondary)
                                    )
                                )
                                .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                .setMaxLines(3)
                                .build()
                        )

                        setPrimaryChipContent(
                            IconButton(
                                context,
                                resourceId = ID_PHONEDISCONNECTED,
                                contentDescription = context.getString(R.string.status_disconnected),
                                size = SMALL_CIRCLE_SIZE,
                                iconSize = SMALL_ICON_SIZE
                            )
                        )
                    }
                }
            }
            .build()
    } else if (state.isEmpty || state.playbackState == null || state.playbackState == PlaybackState.NONE) {
        return PrimaryLayout.Builder(deviceParameters)
            .setContent(
                Text.Builder(context, context.getString(R.string.message_playback_stopped))
                    .setMaxLines(1)
                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                    .setOverflow(TEXT_OVERFLOW_MARQUEE)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(
                        ColorBuilders.argb(
                            ContextCompat.getColor(context, R.color.colorSecondary)
                        )
                    )
                    .build()
            )
            .setPrimaryChipContent(
                CompactChip.Builder(
                    context,
                    context.getString(R.string.action_play),
                    Clickable.Builder()
                        .setId(PlayerAction.PLAY.name)
                        .setOnClick(
                            ActionBuilders.LoadAction.Builder()
                                .build()
                        )
                        .build(),
                    deviceParameters
                )
                    .build()
            )
            .build()
    } else {
        return Box.Builder()
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
                        PrimaryLayout.Builder(deviceParameters)
                            .setPrimaryLabelTextContent(
                                Column.Builder()
                                    .setWidth(expand())
                                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                                    .apply {
                                        val isRound =
                                            deviceParameters.screenShape == SCREEN_SHAPE_ROUND

                                        if (!state.title.isNullOrBlank()) {
                                            addContent(
                                                Text.Builder(context, state.title)
                                                    .setTypography(Typography.TYPOGRAPHY_BUTTON)
                                                    .setColor(
                                                        ColorProp.Builder(Color.WHITE)
                                                            .build()
                                                    )
                                                    .setMaxLines(1)
                                                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                                    .setOverflow(TEXT_OVERFLOW_MARQUEE)
                                                    .setModifiers(
                                                        Modifiers.Builder()
                                                            .setPadding(
                                                                Padding.Builder()
                                                                    .setTop(dp(2f))
                                                                    .setBottom(dp(0.8f))
                                                                    .setStart(
                                                                        if (isRound) dp(32f) else dp(
                                                                            8f
                                                                        )
                                                                    )
                                                                    .setEnd(
                                                                        if (isRound) dp(32f) else dp(
                                                                            8f
                                                                        )
                                                                    )
                                                                    .build()
                                                            )
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                        }

                                        if (!state.artist.isNullOrBlank()) {
                                            addContent(
                                                Text.Builder(context, state.artist)
                                                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                                                    .setColor(
                                                        ColorProp.Builder(Color.WHITE).build()
                                                    )
                                                    .setMaxLines(1)
                                                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                                    .setOverflow(TEXT_OVERFLOW_MARQUEE)
                                                    .setModifiers(
                                                        Modifiers.Builder()
                                                            .setPadding(
                                                                Padding.Builder()
                                                                    .setTop(dp(2f))
                                                                    .setBottom(dp(0.6f))
                                                                    .setStart(
                                                                        if (isRound) dp(32f) else dp(
                                                                            8f
                                                                        )
                                                                    )
                                                                    .setEnd(
                                                                        if (isRound) dp(32f) else dp(
                                                                            8f
                                                                        )
                                                                    )
                                                                    .build()
                                                            )
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                        }
                                    }
                                    .build()
                            )
                            .setContent(
                                MultiSlotLayout.Builder()
                                    .addSlotContent(
                                        PlayerButton(deviceParameters, PlayerAction.PREVIOUS)
                                    )
                                    .apply {
                                        val playerButtonContent =
                                            if (state.playbackState != PlaybackState.PLAYING) {
                                            PlayerButton(deviceParameters, PlayerAction.PLAY)
                                        } else {
                                            PlayerButton(deviceParameters, PlayerAction.PAUSE)
                                        }

                                        addSlotContent(
                                            if (deviceParameters.supportsDynamicValue() && state.positionState != null) {
                                                val actualPercent =
                                                    state.positionState.currentPositionMs.toFloat() / state.positionState.durationMs.toFloat()

                                                Box.Builder()
                                                    .setWidth(dp(56f))
                                                    .setHeight(dp(56f))
                                                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                                                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                                                    .addContent(playerButtonContent)
                                                    .addContent(
                                                        CircularProgressIndicator.Builder()
                                                            .setStartAngle(0f)
                                                            .setEndAngle(360f)
                                                            .setCircularProgressIndicatorColors(
                                                                ProgressIndicatorColors(
                                                                    Colors.DEFAULT.primary,
                                                                    0x25FFFFFF.toInt()
                                                                )
                                                            )
                                                            .setStrokeWidth(dp(3f))
                                                            .setOuterMarginApplied(false)
                                                            .setProgress(
                                                                FloatProp.Builder(actualPercent)
                                                                    .apply {
                                                                        if (state.playbackState == PlaybackState.PLAYING) {
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

                                                                            setDynamicValue(
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
                                                                            )
                                                                        }
                                                                    }
                                                                    .build()
                                                            )
                                                            .build()
                                                    )
                                                    .build()
                                            } else {
                                                playerButtonContent
                                            }
                                        )
                                    }
                                    .addSlotContent(
                                        PlayerButton(deviceParameters, PlayerAction.NEXT)
                                    )
                                    .build()
                            )
                            .setPrimaryChipContent(
                                Row.Builder()
                                    .setWidth(wrap())
                                    .setHeight(wrap())
                                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                                    .addContent(
                                        VolumeButton(PlayerAction.VOL_DOWN)
                                    )
                                    .apply {
                                        if (state.appIcon != null) {
                                            addContent(
                                                Spacer.Builder()
                                                    .setWidth(dp(12f))
                                                    .build()
                                            )
                                            addContent(
                                                Image.Builder()
                                                    .setResourceId(ID_APPICON)
                                                    .setWidth(dp(24f))
                                                    .setHeight(dp(24f))
                                                    .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
                                                    .build()
                                            )
                                            addContent(
                                                Spacer.Builder()
                                                    .setWidth(dp(12f))
                                                    .build()
                                            )
                                        } else {
                                            addContent(
                                                Spacer.Builder()
                                                    .setWidth(dp(24f))
                                                    .build()
                                            )
                                        }
                                    }
                                    .addContent(
                                        VolumeButton(PlayerAction.VOL_UP)
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

private fun PlayerButton(
    deviceParameters: DeviceParameters,
    action: PlayerAction
): LayoutElement {
    val isPlayPause = action == PlayerAction.PAUSE || action == PlayerAction.PLAY
    val size = dp(50f)
    return Box.Builder()
        .setHeight(size)
        .setWidth(size)
        .setModifiers(
            Modifiers.Builder()
                .setBackground(
                    Background.Builder()
                        .setColor(
                            ColorProp.Builder(
                                if (isPlayPause) {
                                    0x19FFFFFF
                                } else {
                                    Color.TRANSPARENT
                                }
                            ).build()
                        )
                        .setCorner(
                            Corner.Builder()
                                .setRadius(size)
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
        .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        .addContent(
            Image.Builder()
                .setWidth(dp(32f))
                .setHeight(dp(32f))
                .setResourceId(getResourceIdForPlayerAction(action))
                .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
                .build()
        )
        .build()
}

private fun VolumeButton(
    action: PlayerAction
): LayoutElement = Box.Builder()
    .setHeight(dp(26f))
    .setWidth(dp(26f))
    .setModifiers(
        Modifiers.Builder()
            .setBackground(
                Background.Builder()
                    .setColor(
                        ColorProp.Builder(Color.TRANSPARENT).build()
                    )
                    .setCorner(
                        Corner.Builder()
                            .setRadius(dp(26f))
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
    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
    .addContent(
        Image.Builder()
            .setWidth(dp(26f))
            .setHeight(dp(26f))
            .setResourceId(getResourceIdForPlayerAction(action))
            .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
            .build()
    )
    .build()

private fun IconButton(
    context: Context,
    resourceId: String,
    contentDescription: String = "",
    clickable: Clickable = Clickable.Builder().build(),
    size: DimensionBuilders.DpProp? = CIRCLE_SIZE,
    iconSize: DimensionBuilders.DpProp = ICON_SIZE
) = Button.Builder(context, clickable)
    .setContentDescription(contentDescription)
    .setButtonColors(ButtonColors.primaryButtonColors(COLORS))
    .setIconContent(resourceId, iconSize)
    .apply {
        if (size != null) {
            setSize(size)
        }
    }
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

@WearTilePreviewDevices
private fun MediaPlayerTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        title = "Title",
        artist = "Artist",
        playbackState = PlaybackState.PAUSED,
        audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
        artwork = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ws_full_sad)?.toBitmapOrNull()
                ?.toByteArray()
        },
        appIcon = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ic_play_circle_simpleblue)
                ?.toBitmapOrNull()
                ?.toByteArray()
        }
    )
    val renderer = MediaPlayerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(state, it) }
    )
}