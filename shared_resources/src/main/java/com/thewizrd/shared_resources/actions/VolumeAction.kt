package com.thewizrd.shared_resources.actions

class VolumeAction(var valueDirection: ValueDirection, var streamType: AudioStreamType?) :
    ValueAction(Actions.VOLUME, valueDirection) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VolumeAction) return false
        if (!super.equals(other)) return false

        if (valueDirection != other.valueDirection) return false
        if (streamType != other.streamType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + valueDirection.hashCode()
        result = 31 * result + (streamType?.hashCode() ?: 0)
        return result
    }
}