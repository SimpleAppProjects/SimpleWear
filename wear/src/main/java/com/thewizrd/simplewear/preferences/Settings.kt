package com.thewizrd.simplewear.preferences

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.reflect.TypeToken
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.App

object Settings {
    const val TAG = "Settings"
    const val KEY_LAYOUTMODE = "key_layoutmode"
    private const val KEY_AUTOLAUNCH = "key_autolaunchmediactrls"
    private const val KEY_MUSICFILTER = "key_musicplayerfilter"
    private const val KEY_LOADAPPICONS = "key_loadappicons"
    private const val KEY_DASHTILECONFIG = "key_dashtileconfig"
    const val KEY_DASHCONFIG = "key_dashconfig"

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

    fun getDashboardTileConfig(): List<Actions>? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        val configJSON = preferences.getString(KEY_DASHTILECONFIG, null)
        return configJSON?.let {
            val arrListType = object : TypeToken<List<Actions>>() {}.type
            JSONParser.deserializer<List<Actions>>(it, arrListType)
        }
    }

    fun setDashboardTileConfig(actions: List<Actions>?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putString(KEY_DASHTILECONFIG, actions?.let {
                val arrListType = object : TypeToken<List<Actions>>() {}.type
                JSONParser.serializer(it, arrListType)
            })
        }
    }

    fun getDashboardConfig(): List<Actions>? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        val configJSON = preferences.getString(KEY_DASHCONFIG, null)
        return configJSON?.let {
            val arrListType = object : TypeToken<List<Actions>>() {}.type
            JSONParser.deserializer<List<Actions>>(it, arrListType)
        }
    }

    fun setDashboardConfig(actions: List<Actions>?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putString(KEY_DASHCONFIG, actions?.let {
                val arrListType = object : TypeToken<List<Actions>>() {}.type
                JSONParser.serializer(it, arrListType)
            })
        }
    }
}