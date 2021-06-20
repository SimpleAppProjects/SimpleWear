@file:JvmName("SerializationUtils")

package com.thewizrd.shared_resources.utils

import android.os.Build
import java.nio.ByteBuffer
import java.nio.charset.Charset

fun Boolean.booleanToBytes(): ByteArray {
    return byteArrayOf((if (this) 1 else 0).toByte())
}

fun ByteArray.bytesToBool(): Boolean {
    return this[0] != 0.toByte()
}

fun String.stringToBytes(): ByteArray {
    return this.toByteArray(Charset.forName("UTF-8"))
}

fun ByteArray.bytesToString(): String {
    return String(this)
}

fun Int.intToBytes(): ByteArray {
    val buf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ByteBuffer.allocate(Integer.BYTES)
    } else {
        ByteBuffer.allocate(4)
    }
    return buf.putInt(this).array()
}

fun ByteArray.bytesToInt(): Int {
    return ByteBuffer.wrap(this).int
}

fun Long.longToBytes(): ByteArray {
    val buf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ByteBuffer.allocate(java.lang.Long.BYTES)
    } else {
        ByteBuffer.allocate(8)
    }
    return buf.putLong(this).array()
}

fun ByteArray.bytesToLong(): Long {
    return ByteBuffer.wrap(this).long
}