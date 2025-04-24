@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.media

import android.content.Context
import android.os.Vibrator
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.VolumeViewModel

class MediaVolumeViewModel(context: Context, mediaPlayerViewModel: MediaPlayerViewModel) :
    VolumeViewModel(
        volumeRepository = MediaVolumeRepository(mediaPlayerViewModel),
        audioOutputRepository = NoopAudioOutputRepository(),
        onCleared = {

        },
        vibrator = context.getSystemService(Vibrator::class.java)
    )