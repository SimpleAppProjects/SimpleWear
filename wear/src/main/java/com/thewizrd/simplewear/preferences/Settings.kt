package com.thewizrd.simplewear.preferences

import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.utils.JSONParser
import java.time.Instant

object Settings {
    const val TAG = "Settings"
    const val KEY_LAYOUTMODE = "key_layoutmode"
    private const val KEY_AUTOLAUNCH = "key_autolaunchmediactrls"
    private const val KEY_MUSICFILTER = "key_musicplayerfilter"
    private const val KEY_LOADAPPICONS = "key_loadappicons"
    private const val KEY_DASHTILECONFIG = "key_dashtileconfig"
    const val KEY_DASHCONFIG = "key_dashconfig"
    const val KEY_SHOWBATSTATUS = "key_showbatstatus"
    const val KEY_SHOWTILEBATSTATUS = "key_showtilebatstatus"
    private const val KEY_LASTUPDATECHECK = "key_lastupdatecheck"

    fun useGridLayout(): Boolean {
        return appLib.preferences.getBoolean(KEY_LAYOUTMODE, true)
    }

    fun setGridLayout(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_LAYOUTMODE, value)
        }
    }

    val isAutoLaunchMediaCtrlsEnabled: Boolean
        get() {
            return appLib.preferences.getBoolean(KEY_AUTOLAUNCH, true)
        }

    fun setAutoLaunchMediaCtrls(enabled: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_AUTOLAUNCH, enabled)
        }
    }

    fun getMusicPlayersFilter(): Set<String> {
        return appLib.preferences.getStringSet(KEY_MUSICFILTER, emptySet()) ?: emptySet()
    }

    fun setMusicPlayersFilter(c: Set<String>) {
        appLib.preferences.edit {
            putStringSet(KEY_MUSICFILTER, c)
        }
    }

    fun isLoadAppIcons(): Boolean {
        return appLib.preferences.getBoolean(KEY_LOADAPPICONS, false)
    }

    fun setLoadAppIcons(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_LOADAPPICONS, value)
        }
    }

    fun getDashboardTileConfig(): List<Actions>? {
        val configJSON = appLib.preferences.getString(KEY_DASHTILECONFIG, null)
        return configJSON?.let {
            val arrListType = object : TypeToken<List<Actions>>() {}.type
            JSONParser.deserializer<List<Actions>>(it, arrListType)
        }
    }

    fun setDashboardTileConfig(actions: List<Actions>?) {
        appLib.preferences.edit {
            putString(KEY_DASHTILECONFIG, actions?.let {
                val arrListType = object : TypeToken<List<Actions>>() {}.type
                JSONParser.serializer(it, arrListType)
            })
        }
    }

    fun getDashboardConfig(): List<Actions>? {
        val configJSON = appLib.preferences.getString(KEY_DASHCONFIG, null)
        return configJSON?.let {
            val arrListType = object : TypeToken<List<Actions>>() {}.type
            JSONParser.deserializer<List<Actions>>(it, arrListType)
        }
    }

    fun setDashboardConfig(actions: List<Actions>?) {
        appLib.preferences.edit {
            putString(KEY_DASHCONFIG, actions?.let {
                val arrListType = object : TypeToken<List<Actions>>() {}.type
                JSONParser.serializer(it, arrListType)
            })
        }
    }

    fun isShowBatStatus(): Boolean {
        return appLib.preferences.getBoolean(KEY_SHOWBATSTATUS, true)
    }

    fun setShowBatStatus(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_SHOWBATSTATUS, value)
        }
    }

    fun isShowTileBatStatus(): Boolean {
        return appLib.preferences.getBoolean(KEY_SHOWTILEBATSTATUS, true)
    }

    fun setShowTileBatStatus(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_SHOWTILEBATSTATUS, value)
        }
    }

    fun getLastUpdateCheckTime(): Instant {
        val epochSeconds =
            appLib.preferences.getLong(KEY_LASTUPDATECHECK, Instant.EPOCH.epochSecond)
        return Instant.ofEpochSecond(epochSeconds)
    }

    fun setLastUpdateCheckTime(value: Instant) {
        appLib.preferences.edit {
            putLong(KEY_LASTUPDATECHECK, value.epochSecond)
        }
    }
}