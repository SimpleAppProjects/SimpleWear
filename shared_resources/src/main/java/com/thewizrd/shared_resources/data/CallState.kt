package com.thewizrd.shared_resources.data

data class CallState(
    val callerName: String? = null,
    val callerBitmap: ByteArray? = null,
    val callActive: Boolean = false,
    val supportedFeatures: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallState) return false

        if (callerName != other.callerName) return false
        if (callerBitmap != null) {
            if (other.callerBitmap == null) return false
            if (!callerBitmap.contentEquals(other.callerBitmap)) return false
        } else if (other.callerBitmap != null) return false
        if (callActive != other.callActive) return false
        if (supportedFeatures != other.supportedFeatures) return false

        return true
    }

    override fun hashCode(): Int {
        var result = callerName?.hashCode() ?: 0
        result = 31 * result + (callerBitmap?.contentHashCode() ?: 0)
        result = 31 * result + callActive.hashCode()
        result = 31 * result + supportedFeatures
        return result
    }
}
