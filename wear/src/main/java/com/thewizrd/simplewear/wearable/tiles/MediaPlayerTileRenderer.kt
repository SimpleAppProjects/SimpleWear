package com.thewizrd.simplewear.wearable.tiles

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
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
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.images.drawableResToImageResource
import com.google.android.horologist.tiles.render.SingleTileLayoutRenderer
import com.thewizrd.simplewear.BuildConfig
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.wearable.tiles.layouts.MediaPlayerTileLayout
import timber.log.Timber
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalHorologistApi::class)
class MediaPlayerTileRenderer(context: Context, debugResourceMode: Boolean = false) :
    SingleTileLayoutRenderer<MediaPlayerTileState, MediaPlayerTileState>(
        context,
        debugResourceMode
    ) {
    companion object {
        // Resource identifiers for images
        internal const val ID_OPENONPHONE = "open_on_phone"
        internal const val ID_PHONEDISCONNECTED = "phone_disconn"

        internal const val ID_ARTWORK = "artwork"
        internal const val ID_PREVIOUS = "prev"
        internal const val ID_PLAY = "play"
        internal const val ID_PAUSE = "pause"
        internal const val ID_SKIP = "skip"
        internal const val ID_VOL_UP = "vol_up"
        internal const val ID_VOL_DOWN = "vol_down"
    }

    private var state: MediaPlayerTileState? = null

    override fun renderTile(
        state: MediaPlayerTileState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        this.state = state

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
                if (state.isEmpty) {
                    PrimaryLayout.Builder(deviceParameters)
                        .setContent(
                            Text.Builder(context, context.getString(R.string.state_loading))
                                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                .setColor(
                                    ColorBuilders.argb(
                                        ContextCompat.getColor(context, R.color.colorSecondary)
                                    )
                                )
                                .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                                .setMaxLines(1)
                                .build()
                        )
                        .setPrimaryChipContent(
                            CompactChip.Builder(
                                context,
                                context.getString(R.string.action_refresh),
                                Clickable.Builder()
                                    .setOnClick(
                                        ActionBuilders.LoadAction.Builder().build()
                                    )
                                    .build(),
                                deviceParameters
                            )
                                .build()
                        )
                        .build()
                } else {
                    MediaPlayerTileLayout(context, deviceParameters, state)
                }
            )
            .build()
    }

    override fun ResourceBuilders.Resources.Builder.produceRequestedResources(
        resourceState: MediaPlayerTileState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        resourceIds: List<String>
    ) {
        Timber.tag(this::class.java.name).d("produceRequestedResources")

        val resources = mapOf(
            ID_OPENONPHONE to R.drawable.common_full_open_on_phone,
            ID_PHONEDISCONNECTED to R.drawable.ic_phonelink_erase_white_24dp,

            ID_PLAY to R.drawable.ic_play_arrow_white_24dp,
            ID_PAUSE to R.drawable.ic_baseline_pause_24,
            ID_PREVIOUS to R.drawable.ic_baseline_skip_previous_24,
            ID_SKIP to R.drawable.ic_baseline_skip_next_24,

            ID_VOL_UP to R.drawable.ic_volume_up_white_24dp,
            ID_VOL_DOWN to R.drawable.ic_baseline_volume_down_24
        )

        Timber.tag(this::class.java.name).e("res - resIds = $resourceIds")

        (resourceIds.takeIf { it.isNotEmpty() } ?: resources.keys).forEach { key ->
            resources[key]?.let { resId ->
                addIdToImageMapping(key, drawableResToImageResource(resId))
            }
        }

        state?.artwork?.let { bitmap ->
            if (resourceIds.isEmpty() || resourceIds.contains(ID_ARTWORK)) {
                addIdToImageMapping(
                    ID_ARTWORK,
                    bitmap.run {
                        val buffer = ByteArrayOutputStream().apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                compress(Bitmap.CompressFormat.WEBP_LOSSY, 0, this)
                            } else {
                                compress(Bitmap.CompressFormat.JPEG, 0, this)
                            }
                        }.toByteArray()

                        ImageResource.Builder()
                            .setInlineResource(
                                InlineImageResource.Builder()
                                    .setData(buffer)
                                    .setWidthPx(width)
                                    .setHeightPx(height)
                                    .setFormat(IMAGE_FORMAT_UNDEFINED)
                                    .build()
                            )
                            .build()
                    }
                )
            }
        }
    }

    override fun getResourcesVersionForTileState(state: MediaPlayerTileState): String {
        return "${state.title}:${state.artist}"
    }

    private fun getTapAction(context: Context): ActionBuilders.Action {
        return ActionBuilders.launchAction(
            ComponentName(context.packageName, context.packageName.run {
                if (BuildConfig.DEBUG) removeSuffix(".debug") else this
            } + ".MediaControllerActivity")
        )
    }
}