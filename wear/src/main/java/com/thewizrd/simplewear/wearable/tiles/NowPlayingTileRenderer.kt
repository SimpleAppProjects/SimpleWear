@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.wearable.tiles

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.IMAGE_FORMAT_UNDEFINED
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.InlineImageResource
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.images.drawableResToImageResource
import com.google.android.horologist.tiles.render.SingleTileLayoutRendererWithState
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.BuildConfig
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.wearable.tiles.layouts.NowPlayingTileLayout
import kotlin.math.min

class NowPlayingTileRenderer(context: Context, debugResourceMode: Boolean = false) :
    SingleTileLayoutRendererWithState<MediaPlayerTileState, MediaPlayerTileState>(
        context,
        debugResourceMode
    ) {
    companion object {
        // Resource identifiers for images
        internal const val ID_OPENONPHONE = "open_on_phone"
        internal const val ID_PHONEDISCONNECTED = "phone_disconn"

        internal const val ID_ARTWORK = "artwork"
        internal const val ID_APPICON = "app_icon"
        internal const val ID_PLAYINGICON = "playing_icon"

        fun getTapAction(context: Context): ActionBuilders.Action {
            return ActionBuilders.launchAction(
                ComponentName(context.packageName, context.packageName.run {
                    if (BuildConfig.DEBUG) removeSuffix(".debug") else this
                } + ".MediaControllerActivity")
            )
        }
    }

    override fun renderTile(
        state: MediaPlayerTileState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        Clickable.Builder()
                            .setOnClick(
                                getTapAction(context)
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                NowPlayingTileLayout(context, deviceParameters, state)
            )
            .build()
    }

    @androidx.annotation.OptIn(ProtoLayoutExperimental::class)
    override fun ResourceBuilders.Resources.Builder.produceRequestedResources(
        resourceState: MediaPlayerTileState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        resourceIds: List<String>
    ) {
        Logger.debug(this::class.java.name, "produceRequestedResources: resIds = $resourceIds")

        val resources = mapOf(
            ID_OPENONPHONE to R.drawable.common_full_open_on_phone,
            ID_PHONEDISCONNECTED to R.drawable.ic_phonelink_erase_white_24dp
        )

        (resourceIds.takeIf { it.isNotEmpty() } ?: resources.keys).forEach { key ->
            resources[key]?.let { resId ->
                addIdToImageMapping(key, drawableResToImageResource(resId))
            }
        }

        resourceState.artwork?.let { bitmap ->
            if (resourceIds.isEmpty() || resourceIds.contains(ID_ARTWORK)) {
                addIdToImageMapping(
                    ID_ARTWORK,
                    ImageResource.Builder()
                        .setInlineResource(
                            InlineImageResource.Builder()
                                .setData(bitmap)
                                .setWidthPx(300)
                                .setHeightPx(300)
                                .setFormat(IMAGE_FORMAT_UNDEFINED)
                                .build()
                        )
                        .build()
                )
            }
        }

        resourceState.appIcon?.let { bitmap ->
            if (resourceIds.isEmpty() || resourceIds.contains(ID_APPICON)) {
                val size = context.dpToPx(24f).toInt()

                addIdToImageMapping(
                    ID_APPICON,
                    ImageResource.Builder()
                        .setInlineResource(
                            InlineImageResource.Builder()
                                .setData(bitmap)
                                .setWidthPx(size)
                                .setHeightPx(size)
                                .setFormat(IMAGE_FORMAT_UNDEFINED)
                                .build()
                        )
                        .build()
                )
            }
        }

        if (resourceIds.isEmpty() || resourceIds.contains(ID_PLAYINGICON)) {
            addIdToImageMapping(
                ID_PLAYINGICON,
                ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.equalizer_animated)
                            .build()
                    )
                    .build()
            )
        }
    }

    override fun getResourcesVersionForTileState(state: MediaPlayerTileState): String {
        return "${state.title}:${state.artist}:${state.artwork?.size}"
    }

    override fun getFreshnessIntervalMillis(state: MediaPlayerTileState): Long {
        return if (state.playbackState == PlaybackState.PLAYING && state.positionState != null) {
            val elapsedTime = System.currentTimeMillis() - state.positionState.currentTimeMs
            val estimatedPosition =
                (state.positionState.currentPositionMs + (elapsedTime * state.positionState.playbackSpeed)).toLong()
            state.positionState.durationMs - min(estimatedPosition, state.positionState.durationMs)
        } else {
            super.getFreshnessIntervalMillis(state)
        }
    }
}