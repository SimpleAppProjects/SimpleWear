package com.thewizrd.shared_resources.media

data class PositionState(
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val currentTimeMs: Long = System.currentTimeMillis()
)
