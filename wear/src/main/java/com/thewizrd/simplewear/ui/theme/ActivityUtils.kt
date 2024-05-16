package com.thewizrd.simplewear.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Find the closest Activity in a given Context.
 */
tailrec fun Context.findActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> throw IllegalStateException(
            "findActivity should be called in the context of an Activity"
        )
    }