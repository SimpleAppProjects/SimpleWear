package com.thewizrd.shared_resources.media

data class MediaMetaData(
    val title: String? = null,
    val artist: String? = null,
    val positionState: PositionState = PositionState(),
)
