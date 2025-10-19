package com.thewizrd.shared_resources.media

data class QueueItems(
    val activeQueueItemId: Long,
    val queueItems: List<QueueItem> = emptyList()
)

data class QueueItem(
    val queueId: Long,
    val title: String,
    val subTitle: String? = null,
    val icon: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueueItem) return false

        if (queueId != other.queueId) return false
        if (title != other.title) return false
        if (subTitle != other.subTitle) return false
        if (icon != null) {
            if (other.icon == null) return false
            if (!icon.contentEquals(other.icon)) return false
        } else if (other.icon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = queueId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + subTitle.hashCode()
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        return result
    }
}