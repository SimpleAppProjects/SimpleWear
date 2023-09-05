package com.thewizrd.simplewear.wearable.tiles

import android.graphics.Bitmap
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MediaPlayerTileModel {
    private var mConnectionStatus = WearConnectionStatus.DISCONNECTED

    private val _tileState =
        MutableStateFlow(
            MediaPlayerTileState(mConnectionStatus, null, null, null, null, null)
        )

    val tileState: StateFlow<MediaPlayerTileState>
        get() = _tileState.asStateFlow()

    fun setConnectionStatus(status: WearConnectionStatus) {
        mConnectionStatus = status
        _tileState.update {
            it.copy(connectionStatus = status)
        }
    }

    fun setPlayerState(
        title: String? = null,
        artist: String? = null,
        artwork: Bitmap? = null,
        playbackState: PlaybackState = PlaybackState.NONE
    ) {
        _tileState.update {
            it.copy(
                title = title,
                artist = artist,
                artwork = artwork,
                playbackState = playbackState
            )
        }
    }

    fun updateArtwork(artwork: Bitmap? = null) {
        _tileState.update {
            it.copy(artwork = artwork)
        }
    }

    fun setAudioStreamState(audioStreamState: AudioStreamState? = null) {
        _tileState.update {
            it.copy(audioStreamState = audioStreamState)
        }
    }
}

data class MediaPlayerTileState(
    val connectionStatus: WearConnectionStatus,

    val title: String?,
    val artist: String?,
    val artwork: Bitmap?,
    val playbackState: PlaybackState? = null,

    val audioStreamState: AudioStreamState?
) {
    val isEmpty = audioStreamState == null || playbackState == null
}