package com.thewizrd.simplewear.wearable.tiles.layouts

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tiles.tooling.preview.TilePreviewHelper
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.theme.wearTileColorScheme
import com.thewizrd.simplewear.ui.tiles.tools.WearPreviewDevices

internal fun LoadingTileLayout(
    context: Context,
    deviceParameters: DeviceParameters
): LayoutElement =
    materialScope(context, deviceParameters, defaultColorScheme = wearTileColorScheme) {
        primaryLayout(
            mainSlot = {
                text(
                    text = context.getString(R.string.state_loading).layoutString,
                    alignment = TEXT_ALIGN_CENTER,
                    maxLines = 3
                )
            },
            bottomSlot = {
                textEdgeButton(
                    onClick = clickable(loadAction()),
                    labelContent = {
                        text(context.getString(R.string.action_refresh).layoutString)
                    }
                )
            }
        )
    }

@WearPreviewDevices
private fun LoadingTilePreview(context: Context) = TilePreviewData(
    onTileRequest = { request ->
        TilePreviewHelper.singleTimelineEntryTileBuilder(
            LoadingTileLayout(context, request.deviceConfiguration)
        ).build()
    }
)