package com.thewizrd.simplewear.wearable.tiles.layouts

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.LayoutDefaults.DEFAULT_VERTICAL_SPACER_HEIGHT
import androidx.wear.protolayout.material.layouts.MultiSlotLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileMessenger.*
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
                                .setExcludeFontPadding(true)
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
                                .setExcludeFontPadding(true)
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
                        .setId(ID_PLAY)
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
            .apply {
                if (state.artwork != null) {
                    addContent(
                        Image.Builder()
                            .setResourceId(ID_ARTWORK)
                            .setWidth(expand())
                            .setHeight(expand())
                            .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
                            .build()
                    )
                }
            }
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
                                        if (!state.title.isNullOrBlank()) {
                                            addContent(
                                                Text.Builder(context, state.title)
                                                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                                    .setColor(
                                                        ColorProp.Builder(Color.WHITE)
                                                            .build()
                                                    )
                                                    .setMaxLines(1)
                                                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                                    .setOverflow(TEXT_OVERFLOW_MARQUEE)
                                                    .build()
                                            )
                                        }

                                        if (!state.title.isNullOrBlank() && !state.artist.isNullOrBlank()) {
                                            addContent(
                                                Spacer.Builder()
                                                    .setHeight(DEFAULT_VERTICAL_SPACER_HEIGHT)
                                                    .build()
                                            )
                                        }

                                        if (!state.artist.isNullOrBlank()) {
                                            addContent(
                                                Text.Builder(context, state.artist)
                                                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                                                    .setColor(
                                                        ColorProp.Builder(Color.WHITE)
                                                            .build()
                                                    )
                                                    .setMaxLines(1)
                                                    .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                                    .setOverflow(TEXT_OVERFLOW_MARQUEE)
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
                                    .addSlotContent(
                                        if (state.playbackState != PlaybackState.PLAYING) {
                                            PlayerButton(deviceParameters, PlayerAction.PLAY)
                                        } else {
                                            PlayerButton(deviceParameters, PlayerAction.PAUSE)
                                        }
                                    )
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
                                    .addContent(
                                        Spacer.Builder()
                                            .setWidth(dp(24f))
                                            .build()
                                    )
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
    val isSmol = minOf(deviceParameters.screenHeightDp, deviceParameters.screenWidthDp) <= 192f
    val imgSize = if (isSmol) dp(44f) else dp(48f)
    return Image.Builder()
        .setWidth(imgSize)
        .setHeight(imgSize)
        .setResourceId(getResourceIdForPlayerAction(action))
        .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
        .setModifiers(
            Modifiers.Builder()
                .setPadding(
                    Padding.Builder()
                        .setAll(dp(8f))
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
        .build()
}

private fun VolumeButton(
    action: PlayerAction
): LayoutElement = Image.Builder()
    .setWidth(ICON_SIZE)
    .setHeight(ICON_SIZE)
    .setResourceId(getResourceIdForPlayerAction(action))
    .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
    .setModifiers(
        Modifiers.Builder()
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