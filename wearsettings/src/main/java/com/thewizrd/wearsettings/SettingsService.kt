package com.thewizrd.wearsettings

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.app.IntentService
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.wearsettings.actions.ActionHelper

class SettingsService : IntentService("SettingsService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return

        var resultReceiver: ResultReceiver? = null
        try {
            val actionData = intent.getParcelableExtra<RemoteAction>(EXTRA_ACTION_DATA) ?: return
            resultReceiver = actionData.resultReceiver ?: return
            val action = actionData.action

            if (action == null) {
                resultReceiver.cancel("Action value null")
                return
            }

            val status = ActionHelper.performAction(this, action)
            action.setActionSuccessful(status)
            val resultCode = if (status == ActionStatus.SUCCESS) RESULT_OK else RESULT_CANCELED
            resultReceiver.send(resultCode, getResultBundle(action))
        } catch (e: Exception) {
            resultReceiver?.cancel(e.toString())
        }
    }

    private fun getResultBundle(action: Action): Bundle {
        return Bundle(1).apply {
            putString(EXTRA_ACTION_DATA, JSONParser.serializer(action, Action::class.java))
        }
    }

    private fun getResultBundle(errorMessage: String): Bundle {
        return Bundle(1).apply {
            putString(EXTRA_ACTION_ERROR, errorMessage)
        }
    }

    private fun ResultReceiver.cancel(errorMessage: String) {
        this.send(RESULT_CANCELED, getResultBundle(errorMessage))
    }
}