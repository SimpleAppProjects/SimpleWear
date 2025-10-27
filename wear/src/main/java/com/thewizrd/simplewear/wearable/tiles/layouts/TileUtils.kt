package com.thewizrd.simplewear.wearable.tiles.layouts

import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.expression.VersionBuilders.VersionInfo
import kotlin.math.max

fun DeviceParameters.supportsTransformation(): Boolean {
    // @RequiresSchemaVersion(major = 1, minor = 400)
    val supportedVersion = VersionInfo.Builder()
        .setMajor(1).setMinor(400)
        .build()

    return this.rendererSchemaVersion >= supportedVersion
}

fun DeviceParameters.supportsDynamicValue(): Boolean {
    // @RequiresSchemaVersion(major = 1, minor = 200)
    val supportedVersion = VersionInfo.Builder()
        .setMajor(1).setMinor(200)
        .build()

    return this.rendererSchemaVersion >= supportedVersion
}

fun DeviceParameters.squareNotSupported(): Boolean {
    // @RequiresSchemaVersion(major = 1, minor = 400)
    val supportedVersion = VersionInfo.Builder()
        .setMajor(1).setMinor(400)
        .build()

    return this.rendererSchemaVersion >= supportedVersion
}

fun DeviceParameters.isSmallWatch(): Boolean {
    return max(screenHeightDp, screenWidthDp) < 225
}

fun DeviceParameters.isLargeWatch(): Boolean {
    return max(screenHeightDp, screenWidthDp) >= 225
}

fun DeviceParameters.isSmallHeight(): Boolean {
    return screenHeightDp < 225
}

fun DeviceParameters.isLargeHeight(): Boolean {
    return screenHeightDp >= 225
}

fun DeviceParameters.isSmallWidth(): Boolean {
    return screenWidthDp < 225
}

fun DeviceParameters.isLargeWidth(): Boolean {
    return screenWidthDp >= 225
}