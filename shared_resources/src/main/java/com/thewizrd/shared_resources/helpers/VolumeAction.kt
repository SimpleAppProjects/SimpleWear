package com.thewizrd.shared_resources.helpers

class VolumeAction(var valueDirection: ValueDirection, var streamType: AudioStreamType?) : ValueAction(Actions.VOLUME, valueDirection)