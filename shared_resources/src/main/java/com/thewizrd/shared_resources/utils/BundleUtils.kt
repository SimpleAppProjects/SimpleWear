package com.thewizrd.shared_resources.utils

import android.os.Bundle
import androidx.core.os.BundleCompat
import java.io.Serializable

fun <T> Bundle.getSerializableCompat(key: String?, clazz: Class<T>): T? where T : Serializable {
    return BundleCompat.getSerializable(this, key, clazz)
}