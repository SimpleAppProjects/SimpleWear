package com.thewizrd.shared_resources.actions

import android.util.SparseArray

enum class AudioStreamType(val value: Int) {
    MUSIC(0),
    RINGTONE(1),
    VOICE_CALL(2),
    ALARM(3);

    companion object {
        private val map = SparseArray<AudioStreamType>()

        fun valueOf(value: Int): AudioStreamType {
            return map[value]
        }

        init {
            for (stream in values()) {
                map.put(stream.value, stream)
            }
        }
    }
}