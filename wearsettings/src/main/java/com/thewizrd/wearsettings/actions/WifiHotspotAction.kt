package com.thewizrd.wearsettings.actions

import android.content.Context
import android.net.IConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import androidx.annotation.DeprecatedSinceApi
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object WifiHotspotAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            return if (Shizuku.pingBinder() && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                setHotspotEnabledShizuku(action.isEnabled)
            } else {
                ActionStatus.REMOTE_FAILURE
            }
        }

        return ActionStatus.UNKNOWN
    }

    private const val TETHERING_WIFI = 0
    private const val TETHER_ERROR_NO_ERROR = 0

    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    private fun setHotspotEnabledShizuku(enabled: Boolean): ActionStatus {
        return runCatching {
            val connMgr = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(IConnectivityManager.Stub::asInterface)

            if (enabled) {
                val resultReceiver = object : ResultReceiver(null) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == TETHER_ERROR_NO_ERROR) {
                            Logger.writeLine(
                                Log.INFO,
                                "WifiHotspotAction: setHotspotEnabledShizuku(true) - success"
                            )
                        } else {
                            Logger.writeLine(
                                Log.ERROR,
                                "WifiHotspotAction: setHotspotEnabledShizuku(true) - failed"
                            )
                        }
                    }
                }

                connMgr.startTethering(TETHERING_WIFI, resultReceiver, false)
            } else {
                connMgr.stopTethering(TETHERING_WIFI)
            }

            ActionStatus.SUCCESS
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }
}