package com.thewizrd.shared_resources.media

data class MediaMetaData(
    val title: String? = null,
    val artist: String? = null,
    val positionState: PositionState = PositionState(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaMetaData) return false

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (positionState != other.positionState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + positionState.hashCode()
        return result
    }
}