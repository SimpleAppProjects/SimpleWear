@file:JvmName("SerializationUtils")

package com.thewizrd.shared_resources.utils

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
    val buf = ByteBuffer.allocate(Int.SIZE_BYTES)
    return buf.putInt(this).array()
}

fun ByteArray.bytesToInt(): Int {
    return ByteBuffer.wrap(this).int
}

fun Long.longToBytes(): ByteArray {
    val buf = ByteBuffer.allocate(Long.SIZE_BYTES)
    return buf.putLong(this).array()
}

fun ByteArray.bytesToLong(): Long {
    return ByteBuffer.wrap(this).long
}

fun Char.charToBytes(): ByteArray {
    val buf = ByteBuffer.allocate(Char.SIZE_BYTES)
    return buf.putChar(this).array()
}

fun ByteArray.bytesToChar(): Char {
    return ByteBuffer.wrap(this).char
}