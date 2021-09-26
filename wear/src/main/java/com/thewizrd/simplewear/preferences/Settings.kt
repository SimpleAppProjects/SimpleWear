package com.thewizrd.simplewear.preferences

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.thewizrd.simplewear.App

object Settings {
    const val TAG = "Settings"
    const val KEY_LAYOUTMODE = "key_layoutmode"
    private const val KEY_AUTOLAUNCH = "key_autolaunchmediactrls"
    private const val KEY_MUSICFILTER = "key_musicplayerfilter"
    private const val KEY_LOADAPPICONS = "key_loadappicons"

    fun useGridLayout(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getBoolean(KEY_LAYOUTMODE, true)
    }

    fun setGridLayout(value: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putBoolean(KEY_LAYOUTMODE, value)
        }
    }

    val isAutoLaunchMediaCtrlsEnabled: Boolean
        get() {
            val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
            return preferences.getBoolean(KEY_AUTOLAUNCH, true)
        }

    fun setAutoLaunchMediaCtrls(enabled: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putBoolean(KEY_AUTOLAUNCH, enabled)
        }
    }

    fun getMusicPlayersFilter(): Set<String> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getStringSet(KEY_MUSICFILTER, emptySet()) ?: emptySet()
    }

    fun setMusicPlayersFilter(c: Set<String>) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putStringSet(KEY_MUSICFILTER, c)
        }
    }

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