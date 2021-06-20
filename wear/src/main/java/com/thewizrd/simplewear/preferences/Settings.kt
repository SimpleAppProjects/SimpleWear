package com.thewizrd.simplewear.preferences

import androidx.preference.PreferenceManager
import com.thewizrd.simplewear.App

object Settings {
    const val TAG = "Settings"
    const val KEY_LAYOUTMODE = "key_layoutmode"
    const val KEY_AUTOLAUNCH = "key_autolaunchmediactrls"

    fun useGridLayout(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getBoolean(KEY_LAYOUTMODE, true)
    }

    fun setGridLayout(value: Boolean) {
        val editor = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
            .edit()
        editor.putBoolean(KEY_LAYOUTMODE, value)
        editor.apply()
    }

    val isAutoLaunchMediaCtrlsEnabled: Boolean
        get() {
            val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
            return preferences.getBoolean(KEY_AUTOLAUNCH, true)
        }

    fun setAutoLaunchMediaCtrls(enabled: Boolean) {
        val editor = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
            .edit()
        editor.putBoolean(KEY_AUTOLAUNCH, enabled)
        editor.apply()
    }
}