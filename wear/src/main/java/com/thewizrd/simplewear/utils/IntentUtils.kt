package com.thewizrd.simplewear.utils

import android.content.Intent

fun Intent.asLauncherIntent(): Intent {
    action = Intent.ACTION_MAIN
    addCategory(Intent.CATEGORY_LAUNCHER)
    return this
}