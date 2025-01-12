package com.thewizrd.wearsettings

import androidx.core.content.edit
import com.thewizrd.shared_resources.appLib

object Settings {
    private const val KEY_ROOTACCESS = "key_rootaccess"

    fun isRootAccessEnabled(): Boolean {
        return appLib.preferences.getBoolean(KEY_ROOTACCESS, false)
    }

    fun setRootAccessEnabled(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_ROOTACCESS, value)
        }
    }
}