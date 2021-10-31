package com.thewizrd.wearsettings

import android.preference.PreferenceManager
import androidx.core.content.edit

object Settings {
    private const val KEY_ROOTACCESS = "key_rootaccess"

    fun isRootAccessEnabled(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getBoolean(KEY_ROOTACCESS, false)
    }

    fun setRootAccessEnabled(value: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putBoolean(KEY_ROOTACCESS, value)
        }
    }
}