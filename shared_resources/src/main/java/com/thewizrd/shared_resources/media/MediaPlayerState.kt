package com.thewizrd.shared_resources.media

data class MediaPlayerState(
    val playbackState: PlaybackState = PlaybackState.NONE,
    val mediaMetaData: MediaMetaData? = null
) {
    val key = "${playbackState}|${mediaMetaData?.title}|${mediaMetaData?.artist}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaPlayerState) return false

        if (playbackState != other.playbackState) return false
        if (mediaMetaData != other.mediaMetaData) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playbackState.hashCode()
        result = 31 * result + (mediaMetaData?.hashCode() ?: 0)
        return result
    }
}