package com.thewizrd.simplewear.ui.utils

import android.widget.TextView
import androidx.annotation.StringRes

fun TextView.setTextResId(@StringRes resId: Int) {
    if (resId != 0) {
        setText(resId)
    } else {
        text = null
    }
}