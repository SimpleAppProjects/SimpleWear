package com.thewizrd.simplewear.ui.tiles.tools

import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices

@Preview(
    device = WearDevices.LARGE_ROUND,
    name = "Wear - Large Round"
)
@Preview(
    device = WearDevices.SMALL_ROUND,
    name = "Wear - Small Round"
)
@Preview(
    device = WearDevices.SQUARE,
    name = "Wear - Square"
)
@Preview(
    device = WearDevices.RECT,
    name = "Wear - Rect"
)
annotation class WearPreviewDevices