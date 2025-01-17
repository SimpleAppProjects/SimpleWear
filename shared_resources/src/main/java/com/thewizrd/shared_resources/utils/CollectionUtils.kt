@file:JvmName("CollectionUtils")

package com.thewizrd.shared_resources.utils

import java.util.Objects

fun sequenceEqual(iterable1: Iterable<*>?, iterable2: Iterable<*>?): Boolean {
    if (iterable1 is Collection && iterable2 is Collection) {
        if (iterable1.size != iterable2.size) {
            return false
        }

        if (iterable1 is List && iterable2 is List) {
            val count = iterable1.size
            for (i in 0 until count) {
                if (!Objects.equals(iterable1[i], iterable2[i])) {
                    return false
                }
            }

            return true
        }
    }

    return sequenceEqual(iterable1?.iterator(), iterable2?.iterator())
}

fun sequenceEqual(iterator1: Iterator<*>?, iterator2: Iterator<*>?): Boolean {
    while (iterator1?.hasNext() == true) {
        if (iterator2?.hasNext() != true) {
            return false
        }
        val o1 = iterator1.next()
        val o2 = iterator2.next()
        if (!Objects.equals(o1, o2)) {
            return false
        }
    }
    return iterator2?.hasNext() != true
}