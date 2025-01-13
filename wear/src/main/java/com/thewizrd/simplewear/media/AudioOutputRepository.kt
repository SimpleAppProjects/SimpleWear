package com.thewizrd.simplewear.media

import com.google.android.horologist.audio.AudioOutput
import com.google.android.horologist.audio.AudioOutputRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoopAudioOutputRepository : AudioOutputRepository {
    override val audioOutput: StateFlow<AudioOutput>
        get() = MutableStateFlow(AudioOutput.None).asStateFlow()
    override val available: StateFlow<List<AudioOutput>>
        get() = MutableStateFlow(emptyList())

    override fun close() {}

    override fun launchOutputSelection(closeOnConnect: Boolean) {}
}