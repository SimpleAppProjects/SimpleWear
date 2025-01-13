package com.thewizrd.simplewear.media

import android.os.Build
import android.support.v4.media.session.PlaybackStateCompat
import com.thewizrd.shared_resources.media.PlaybackState

fun PlaybackStateCompat.isPlaybackStateActive(): Boolean {
    return when (state) {
        PlaybackStateCompat.STATE_BUFFERING,
        PlaybackStateCompat.STATE_CONNECTING,
        PlaybackStateCompat.STATE_FAST_FORWARDING,
        PlaybackStateCompat.STATE_PLAYING,
        PlaybackStateCompat.STATE_REWINDING,
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> {
            true
        }

        else -> false
    }
}

fun android.media.session.PlaybackState.isPlaybackStateActive(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.isActive
    } else {
        when (state) {
            android.media.session.PlaybackState.STATE_FAST_FORWARDING,
            android.media.session.PlaybackState.STATE_REWINDING,
            android.media.session.PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            android.media.session.PlaybackState.STATE_SKIPPING_TO_NEXT,
            android.media.session.PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
            android.media.session.PlaybackState.STATE_BUFFERING,
            android.media.session.PlaybackState.STATE_CONNECTING,
            android.media.session.PlaybackState.STATE_PLAYING -> true

            else -> false
        }
    }
}

fun PlaybackStateCompat.toPlaybackState(): PlaybackState {
    return when (state) {
        PlaybackStateCompat.STATE_NONE -> {
            PlaybackState.NONE
        }

        PlaybackStateCompat.STATE_BUFFERING,
        PlaybackStateCompat.STATE_CONNECTING -> {
            PlaybackState.LOADING
        }

        PlaybackStateCompat.STATE_PLAYING,
        PlaybackStateCompat.STATE_FAST_FORWARDING,
        PlaybackStateCompat.STATE_REWINDING,
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> {
            PlaybackState.PLAYING
        }

        PlaybackStateCompat.STATE_PAUSED,
        PlaybackStateCompat.STATE_STOPPED -> {
            PlaybackState.PAUSED
        }

        else -> {
            PlaybackState.NONE
        }
    }
}