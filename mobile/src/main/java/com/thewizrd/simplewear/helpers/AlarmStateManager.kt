package com.thewizrd.simplewear.helpers

import android.content.Context
import androidx.core.content.edit
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.utils.JSONParser

class AlarmStateManager(private val context: Context) {
    companion object {
        private const val KEY_ALARMS = "alarms"
    }

    internal val preferences = context.getSharedPreferences(KEY_ALARMS, Context.MODE_PRIVATE)

    fun saveAlarm(actionType: Actions, action: TimedAction) {
        saveAlarm(actionType, JSONParser.serializer(action, Action::class.java))
    }

    fun saveAlarm(actionType: Actions, actionJsonString: String?) {
        preferences.edit {
            putString(actionType.name, actionJsonString)
        }
    }

    fun clearAlarm(actionType: Actions) {
        preferences.edit {
            remove(actionType.name)
        }
    }

    fun getAlarms(): Map<Actions, TimedAction?> {
        return preferences.all.mapValues {
            JSONParser.deserializer(it.value?.toString(), TimedAction::class.java)
        }.mapKeys {
            Actions.valueOf(it.key)
        }
    }

    fun clearAlarms() {
        preferences.edit {
            clear()
        }
    }
}