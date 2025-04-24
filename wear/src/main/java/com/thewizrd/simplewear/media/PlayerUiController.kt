package com.thewizrd.simplewear.media

interface PlayerUiController {
    fun play()
    fun pause()
    fun skipToPreviousMedia()
    fun skipToNextMedia()
}

class NoopPlayerUiController : PlayerUiController {
    override fun play() {}

    override fun pause() {}

    override fun skipToPreviousMedia() {}

    override fun skipToNextMedia() {}
}

class MediaPlayerUiController(private val mediaPlayerViewModel: MediaPlayerViewModel) :
    PlayerUiController {
    override fun play() = mediaPlayerViewModel.requestPlayPauseAction(play = true)

    override fun pause() = mediaPlayerViewModel.requestPlayPauseAction(play = false)

    override fun skipToPreviousMedia() = mediaPlayerViewModel.requestSkipToPreviousAction()

    override fun skipToNextMedia() = mediaPlayerViewModel.requestSkipToNextAction()
}