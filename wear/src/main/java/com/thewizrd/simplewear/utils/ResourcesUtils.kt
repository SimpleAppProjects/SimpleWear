/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * ResourcesUtil.java
 * platform/frameworks/support
 * branch: pie-release
 */
package com.thewizrd.simplewear.utils

import android.content.Context
import androidx.annotation.FractionRes

/**
 * Utility methods to help with resource calculations.
 *
 * @hide
 */
object ResourcesUtils {
    /**
     * Returns the screen width in pixels.
     */
    fun getScreenWidthPx(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * Returns the screen height in pixels.
     */
    fun getScreenHeightPx(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    /**
     * Returns the number of pixels equivalent to the percentage of `resId` to the current
     * screen.
     */
    fun getFractionOfScreenPx(context: Context, screenPx: Int, @FractionRes resId: Int): Int {
        val marginPercent = context.resources.getFraction(resId, 1, 1)
        return (marginPercent * screenPx).toInt()
    }
}