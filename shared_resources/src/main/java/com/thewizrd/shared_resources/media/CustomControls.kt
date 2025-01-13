package com.thewizrd.shared_resources.media

data class CustomControls(
    val actions: List<ActionItem> = emptyList()
)

data class ActionItem(
    val action: String,
    val title: String,
    val icon: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActionItem) return false

        if (action != other.action) return false
        if (title != other.title) return false
        if (icon != null) {
            if (other.icon == null) return false
            if (!icon.contentEquals(other.icon)) return false
        } else if (other.icon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        return result
    }
}
