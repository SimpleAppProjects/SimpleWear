package com.thewizrd.simplewear.preferences

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.thewizrd.simplewear.App

object Settings {
    private const val KEY_LOADAPPICONS = "key_loadappicons"
    private const val KEY_BRIDGEMEDIA = "key_bridgemedia"
    private const val KEY_BRIDGECALLS = "key_bridgecalls"

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

    fun isBridgeMediaEnabled(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getBoolean(KEY_BRIDGEMEDIA, false)
    }

    fun setBridgeMediaEnabled(value: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putBoolean(KEY_BRIDGEMEDIA, value)
        }
    }

    fun isBridgeCallsEnabled(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getBoolean(KEY_BRIDGECALLS, false)
    }

    fun setBridgeCallsEnabled(value: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putBoolean(KEY_BRIDGECALLS, value)
        }
    }
}