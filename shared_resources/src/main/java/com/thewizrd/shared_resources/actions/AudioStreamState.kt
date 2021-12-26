package com.thewizrd.shared_resources.actions

class AudioStreamState(
    var currentVolume: Int,
    var minVolume: Int,
    var maxVolume: Int,
    var streamType: AudioStreamType
) : ValueActionState(currentVolume, minVolume, maxVolume, Actions.VOLUME)