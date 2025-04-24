package com.thewizrd.simplewear.datastore.media

import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.media.MediaPlayerState

data class MediaDataCache(
    val mediaPlayerState: MediaPlayerState? = null,
    val audioStreamState: AudioStreamState? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaDataCache) return false

        if (mediaPlayerState != other.mediaPlayerState) return false
        if (audioStreamState != other.audioStreamState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mediaPlayerState?.hashCode() ?: 0
        result = 31 * result + (audioStreamState?.hashCode() ?: 0)
        return result
    }
}