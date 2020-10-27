package com.thewizrd.shared_resources.utils

import android.os.Build
import java.nio.ByteBuffer
import java.nio.charset.Charset

public fun Boolean.booleanToBytes(): ByteArray {
    return byteArrayOf((if (this) 1 else 0).toByte())
}

public fun ByteArray.bytesToBool(): Boolean {
    return this[0] != 0.toByte()
}

public fun String.stringToBytes(): ByteArray {
    return this.toByteArray(Charset.forName("UTF-8"))
}

public fun ByteArray.bytesToString(): String {
    return String(this)
}

public fun Int.intToBytes(): ByteArray {
    val buf: ByteBuffer
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        buf = ByteBuffer.allocate(Integer.BYTES)
    } else {
        buf = ByteBuffer.allocate(4)
    }
    return buf.putInt(this).array()
}

public fun ByteArray.bytesToInt(): Int {
    return ByteBuffer.wrap(this).int
}

public fun Long.longToBytes(): ByteArray {
    val buf: ByteBuffer
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        buf = ByteBuffer.allocate(java.lang.Long.BYTES)
    } else {
        buf = ByteBuffer.allocate(8)
    }
    return buf.putLong(this).array()
}

public fun ByteArray.bytesToLong(): Long {
    return ByteBuffer.wrap(this).long
}