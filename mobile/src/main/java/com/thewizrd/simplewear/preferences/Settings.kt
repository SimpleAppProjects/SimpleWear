package com.thewizrd.simplewear.preferences

import androidx.core.content.edit
import com.thewizrd.shared_resources.appLib

object Settings {
    private const val KEY_LOADAPPICONS = "key_loadappicons"
    private const val KEY_BRIDGEMEDIA = "key_bridgemedia"
    private const val KEY_BRIDGECALLS = "key_bridgecalls"
    private const val KEY_VERSIONCODE = "key_versioncode"

    fun isLoadAppIcons(): Boolean {
        return appLib.preferences.getBoolean(KEY_LOADAPPICONS, false)
    }

    fun setLoadAppIcons(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_LOADAPPICONS, value)
        }
    }

    fun isBridgeMediaEnabled(): Boolean {
        return appLib.preferences.getBoolean(KEY_BRIDGEMEDIA, false)
    }

    fun setBridgeMediaEnabled(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_BRIDGEMEDIA, value)
        }
    }

    fun isBridgeCallsEnabled(): Boolean {
        return appLib.preferences.getBoolean(KEY_BRIDGECALLS, false)
    }

    fun setBridgeCallsEnabled(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_BRIDGECALLS, value)
        }
    }

    fun getVersionCode(): Long {
        return appLib.preferences.getLong(KEY_VERSIONCODE, 0)
    }

    fun setVersionCode(value: Long) {
        appLib.preferences.edit {
            putLong(KEY_VERSIONCODE, value)
        }
    }
}