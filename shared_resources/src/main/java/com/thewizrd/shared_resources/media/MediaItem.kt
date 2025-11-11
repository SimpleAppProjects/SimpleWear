package com.thewizrd.shared_resources.media

data class MediaItem(
    val mediaId: String,
    val title: String,
    val subTitle: String? = null,
    val icon: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaItem) return false

        if (mediaId != other.mediaId) return false
        if (title != other.title) return false
        if (subTitle != other.subTitle) return false
        if (icon != null) {
            if (other.icon == null) return false
            if (!icon.contentEquals(other.icon)) return false
        } else if (other.icon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mediaId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + subTitle.hashCode()
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        return result
    }
}