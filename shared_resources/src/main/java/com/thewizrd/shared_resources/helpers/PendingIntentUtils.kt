package com.thewizrd.shared_resources.helpers

import android.app.PendingIntent
import android.os.Build

fun Int.toImmutableCompatFlag(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this or PendingIntent.FLAG_IMMUTABLE
    } else {
        this
    }
}