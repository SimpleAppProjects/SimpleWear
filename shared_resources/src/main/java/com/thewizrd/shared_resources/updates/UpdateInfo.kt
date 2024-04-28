package com.thewizrd.shared_resources.updates

import com.google.gson.annotations.SerializedName

data class UpdateInfo(
    @field:SerializedName("version")
    var versionCode: Long = 0,
    @field:SerializedName("updatePriority")
    var updatePriority: Int = 0
)