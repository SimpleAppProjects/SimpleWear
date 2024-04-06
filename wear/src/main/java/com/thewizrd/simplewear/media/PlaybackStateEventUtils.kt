@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.media

import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.media.model.PlaybackStateEvent
import com.google.android.horologist.media.model.PlayerState
import com.thewizrd.shared_resources.media.PlaybackState
import kotlin.time.Duration.Companion.milliseconds

fun PlaybackState.toPlayerState(): PlayerState {
    return when (this) {
        PlaybackState.NONE -> PlayerState.Stopped
        PlaybackState.LOADING -> PlayerState.Loading
        PlaybackState.PLAYING -> PlayerState.Playing
        PlaybackState.PAUSED -> PlayerState.Idle
    }
}

fun com.thewizrd.simplewear.media.PlayerState.toPlaybackStateEvent(): PlaybackStateEvent {
    return PlaybackStateEvent(
        playbackState = com.google.android.horologist.media.model.PlaybackState(
            playerState = this.playbackState.toPlayerState(),
            isLive = false,
            currentPosition = this.positionState?.currentPositionMs?.milliseconds,
            duration = this.positionState?.durationMs?.milliseconds,
            playbackSpeed = this.positionState?.playbackSpeed ?: 1f
        ),
        cause = PlaybackStateEvent.Cause.PlayerStateChanged,
        timestamp = this.positionState?.currentTimeMs?.milliseconds
    )
}