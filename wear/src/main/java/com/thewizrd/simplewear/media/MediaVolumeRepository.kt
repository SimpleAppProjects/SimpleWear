package com.thewizrd.simplewear.media

import androidx.lifecycle.viewModelScope
import com.google.android.horologist.audio.VolumeRepository
import com.google.android.horologist.audio.VolumeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaVolumeRepository(private val mediaPlayerViewModel: MediaPlayerViewModel) :
    VolumeRepository {
    override val volumeState: StateFlow<VolumeState>
        get() = localVolumeState

    private val localVolumeState = MutableStateFlow(VolumeState(current = 0, max = 1))

    private val remoteVolumeState = mediaPlayerViewModel.uiState.map {
        VolumeState(
            current = it.audioStreamState?.currentVolume ?: 0,
            min = it.audioStreamState?.minVolume ?: 0,
            max = it.audioStreamState?.maxVolume ?: 1
        )
    }

    init {
        mediaPlayerViewModel.viewModelScope.launch(Dispatchers.Default) {
            remoteVolumeState.collectLatest { state ->
                delay(1000)

                if (!isActive) return@collectLatest

                localVolumeState.emit(state)
            }
        }
    }

    override fun close() {}

    override fun decreaseVolume() {
        localVolumeState.update {
            it.copy(current = it.current - 1)
        }
        mediaPlayerViewModel.requestVolumeDown()
    }

    override fun increaseVolume() {
        localVolumeState.update {
            it.copy(current = it.current + 1)
        }
        mediaPlayerViewModel.requestVolumeUp()
    }

    override fun setVolume(volume: Int) {
        localVolumeState.update {
            it.copy(current = volume)
        }
        mediaPlayerViewModel.requestSetVolume(volume)
    }
}