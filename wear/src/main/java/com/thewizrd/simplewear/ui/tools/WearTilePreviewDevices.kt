package com.thewizrd.simplewear.ui.tools

import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices

@Preview(
    device = WearDevices.LARGE_ROUND,
    group = "Devices - Large Round"
)
@Preview(
    device = WearDevices.SMALL_ROUND,
    group = "Devices - Small Round"
)
@Preview(
    device = WearDevices.SQUARE,
    group = "Devices - Square"
)
@Preview(
    device = WearDevices.SMALL_ROUND,
    group = "Devices - Small Round",
    fontScale = 1.5f
)
public annotation class WearTilePreviewDevices

@Preview(
    device = WearDevices.SMALL_ROUND,
    group = "Devices - Small Round"
)
public annotation class WearSmallRoundDeviceTilePreview

@Preview(
    device = WearDevices.LARGE_ROUND,
    group = "Devices - Large Round"
)
public annotation class WearLargeRoundDeviceTilePreview

@Preview(
    device = WearDevices.SQUARE,
    group = "Devices - Square"
)
public annotation class WearSquareDeviceTilePreview