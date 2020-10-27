@file:JvmName("StringUtils")

package com.thewizrd.shared_resources.utils

public fun String.Companion.isNullOrEmpty(s: String?): Boolean {
    return s == null || s.length == 0
}

public fun String.Companion.isNullOrWhitespace(s: String?): Boolean {
    return s == null || isWhitespace(s)
}

private fun isWhitespace(s: String?): Boolean {
    if (s == null)
        return true

    for (idx in 0..s.length) {
        if (!Character.isWhitespace(s.toCharArray()[idx]))
            return false
    }

    return true
}

public fun String.Companion.lineSeparator(): String {
    return System.lineSeparator()
}