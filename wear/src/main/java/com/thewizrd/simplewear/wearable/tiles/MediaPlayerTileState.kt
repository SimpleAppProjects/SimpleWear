package com.thewizrd.simplewear.wearable.tiles

import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.media.PositionState

data class MediaPlayerTileState(
    val connectionStatus: WearConnectionStatus,

    val title: String?,
    val artist: String?,
    val artwork: ByteArray?,
    val playbackState: PlaybackState? = null,
    val positionState: PositionState? = null,

    val audioStreamState: AudioStreamState?,

    val appIcon: ByteArray? = null
) {
    val isEmpty = playbackState == null
    val key = "$playbackState|$title|$artist"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaPlayerTileState) return false

        if (connectionStatus != other.connectionStatus) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (artwork != null) {
            if (other.artwork == null) return false
            if (!artwork.contentEquals(other.artwork)) return false
        } else if (other.artwork != null) return false
        if (playbackState != other.playbackState) return false
        if (positionState != other.positionState) return false
        if (audioStreamState != other.audioStreamState) return false
        if (appIcon != null) {
            if (other.appIcon == null) return false
            if (!appIcon.contentEquals(other.appIcon)) return false
        } else if (other.appIcon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = connectionStatus.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (artwork?.contentHashCode() ?: 0)
        result = 31 * result + (playbackState?.hashCode() ?: 0)
        result = 31 * result + (positionState?.hashCode() ?: 0)
        result = 31 * result + (audioStreamState?.hashCode() ?: 0)
        result = 31 * result + (appIcon?.contentHashCode() ?: 0)
        return result
    }
}