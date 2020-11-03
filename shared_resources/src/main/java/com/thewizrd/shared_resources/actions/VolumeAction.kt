package com.thewizrd.shared_resources.actions

class VolumeAction(var valueDirection: ValueDirection, var streamType: AudioStreamType?) : ValueAction(Actions.VOLUME, valueDirection)