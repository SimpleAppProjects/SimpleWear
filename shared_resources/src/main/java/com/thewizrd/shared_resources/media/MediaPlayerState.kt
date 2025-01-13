package com.thewizrd.shared_resources.media

data class MediaPlayerState(
    val playbackState: PlaybackState = PlaybackState.NONE,
    val mediaMetaData: MediaMetaData? = null
)
