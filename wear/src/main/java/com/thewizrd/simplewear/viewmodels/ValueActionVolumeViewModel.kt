package com.thewizrd.simplewear.viewmodels

import android.content.Context
import android.os.Vibrator
import androidx.lifecycle.viewModelScope
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.VolumeRepository
import com.google.android.horologist.audio.VolumeState
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.thewizrd.simplewear.media.NoopAudioOutputRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalHorologistApi::class)
class ValueActionVolumeViewModel(context: Context, valueActionViewModel: ValueActionViewModel) :
    VolumeViewModel(
        volumeRepository = ValueActionRepository(valueActionViewModel),
        audioOutputRepository = NoopAudioOutputRepository(),
        onCleared = {

        },
        vibrator = context.getSystemService(Vibrator::class.java)
    )

private class ValueActionRepository(private val valueActionViewModel: ValueActionViewModel) :
    VolumeRepository {
    override val volumeState: StateFlow<VolumeState>
        get() = localValueState

    private val localValueState = MutableStateFlow(VolumeState(current = 0, max = 1))

    private val remoteValueState = valueActionViewModel.uiState.map {
        VolumeState(
            current = it.valueActionState?.currentValue ?: 0,
            min = it.valueActionState?.minValue ?: 0,
            max = it.valueActionState?.maxValue ?: 1
        )
    }

    init {
        valueActionViewModel.viewModelScope.launch(Dispatchers.Default) {
            remoteValueState.collectLatest { state ->
                delay(1000)

                if (!isActive) return@collectLatest

                localValueState.emit(state)
            }
        }
    }

    override fun close() {}

    override fun decreaseVolume() {
        localValueState.update {
            it.copy(current = it.current - 1)
        }
        valueActionViewModel.decreaseValue()
    }

    override fun increaseVolume() {
        localValueState.update {
            it.copy(current = it.current + 1)
        }
        valueActionViewModel.increaseValue()
    }

    override fun setVolume(volume: Int) {
        val currentValue = localValueState.value.current

        if (volume > currentValue) {
            increaseVolume()
        } else if (volume < currentValue) {
            decreaseVolume()
        }
    }
}