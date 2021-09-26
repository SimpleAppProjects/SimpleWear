package com.thewizrd.simplewear.preferences

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.thewizrd.simplewear.App

object Settings {
    private const val KEY_LOADAPPICONS = "key_loadappicons"

    fun isLoadAppIcons(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getBoolean(KEY_LOADAPPICONS, false)
    }

    fun setLoadAppIcons(value: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putBoolean(KEY_LOADAPPICONS, value)
        }
    }
}