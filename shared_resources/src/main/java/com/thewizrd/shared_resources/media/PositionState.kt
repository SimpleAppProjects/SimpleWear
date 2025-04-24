package com.thewizrd.shared_resources.media

import java.time.Instant

data class PositionState(
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val currentTimeMs: Long = Instant.now().toEpochMilli()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PositionState) return false

        if (durationMs != other.durationMs) return false
        if (currentPositionMs != other.currentPositionMs) return false
        if (playbackSpeed != other.playbackSpeed) return false
        if (currentTimeMs != other.currentTimeMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = durationMs.hashCode()
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + playbackSpeed.hashCode()
        result = 31 * result + currentTimeMs.hashCode()
        return result
    }
}
