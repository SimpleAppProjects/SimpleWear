package com.thewizrd.wearsettings.actions

import android.annotation.SuppressLint
import android.content.Context
import android.net.IConnectivityManager
import android.net.IIntResultListener
import android.net.ITetheringConnector
import android.net.TetheringRequestParcel
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import androidx.annotation.DeprecatedSinceApi
import androidx.annotation.RequiresApi
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

@SuppressLint("PrivateApi")
object WifiHotspotAction {
    private const val TAG = "WifiHotspotAction"

    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            return if (Shizuku.pingBinder()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setHotspotEnabledShizuku(action.isEnabled)
                } else {
                    setHotspotEnabledShizukuPreR(action.isEnabled)
                }
            } else {
                ActionStatus.REMOTE_FAILURE
            }
        }

        return ActionStatus.UNKNOWN
    }

    /*
     * android.net
     * ConnectivityManager / TetheringManager constants
     */
    /* TetheringType */
    private const val TETHERING_WIFI = 0

    /* TetheringManager service */
    private const val TETHERING_SERVICE = "tethering"

    /* Tether error codes */
    private const val TETHER_ERROR_NO_ERROR = 0
    private const val TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION = 14

    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    private fun setHotspotEnabledShizukuPreR(enabled: Boolean): ActionStatus {
        Logger.info(TAG, "entering setHotspotEnabledShizukuPreR(enabled = ${enabled})...")

        return runCatching {
            val connMgr = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(IConnectivityManager.Stub::asInterface)

            if (enabled) {
                val resultReceiver = object : ResultReceiver(null) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        when (resultCode) {
                            TETHER_ERROR_NO_ERROR -> {
                                Logger.info(TAG, "setHotspotEnabledShizukuPreR(true) - success")
                            }

                            else -> {
                                Logger.error(
                                    TAG,
                                    "setHotspotEnabledShizukuPreR(true) - failed. code = $resultCode"
                                )
                            }
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

    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun setHotspotEnabledShizuku(
        enabled: Boolean,
        exemptFromEntitlementCheck: Boolean = true,
        shouldShowEntitlementUi: Boolean = false
    ): ActionStatus {
        Logger.info(TAG, "entering setHotspotEnabledShizuku(enabled = ${enabled})...")

        return runCatching {
            val tetheringMgr = SystemServiceHelper.getSystemService(TETHERING_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(ITetheringConnector.Stub::asInterface)

            if (enabled) {
                val resultListener = object : IIntResultListener.Stub() {
                    override fun onResult(resultCode: Int) {
                        when (resultCode) {
                            TETHER_ERROR_NO_ERROR -> {
                                Logger.info(TAG, "setHotspotEnabledShizuku(true) - success")
                            }

                            TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION -> {
                                // retry
                                setHotspotEnabledShizuku(enabled, false, shouldShowEntitlementUi)
                            }

                            else -> {
                                Logger.error(
                                    TAG,
                                    "setHotspotEnabledShizuku(true) - failed. code = $resultCode"
                                )
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    tetheringMgr.startTethering(
                        createTetheringRequestParcel(
                            exemptFromEntitlementCheck,
                            shouldShowEntitlementUi
                        ) as TetheringRequestParcel,
                        "com.android.shell",
                        "",
                        resultListener
                    )
                } else {
                    tetheringMgr.startTethering(
                        createTetheringRequestParcel(
                            exemptFromEntitlementCheck,
                            shouldShowEntitlementUi
                        ) as TetheringRequestParcel,
                        "com.android.shell",
                        resultListener
                    )
                }
            } else {
                val resultListener = object : IIntResultListener.Stub() {
                    override fun onResult(resultCode: Int) {
                        when (resultCode) {
                            TETHER_ERROR_NO_ERROR -> {
                                Logger.info(TAG, "setHotspotEnabledShizuku(false) - success")
                            }

                            else -> {
                                Logger.error(
                                    TAG,
                                    "setHotspotEnabledShizuku(false) - failed. code = $resultCode"
                                )
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    tetheringMgr.stopTethering(
                        TETHERING_WIFI,
                        "com.android.shell",
                        "",
                        resultListener
                    )
                } else {
                    tetheringMgr.stopTethering(TETHERING_WIFI, "com.android.shell", resultListener)
                }
            }

            ActionStatus.SUCCESS
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }

    private fun createTetheringRequest(
        exemptFromEntitlementCheck: Boolean = true,
        shouldShowEntitlementUi: Boolean = false
    ): Any {
        return Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder").run {
            val setExemptFromEntitlementCheck =
                getDeclaredMethod("setExemptFromEntitlementCheck", Boolean::class.java)
            val setShouldShowEntitlementUi =
                getDeclaredMethod("setShouldShowEntitlementUi", Boolean::class.java)
            val build = getDeclaredMethod("build")

            getConstructor(Int::class.java).run {
                this.newInstance(TETHERING_WIFI).let {
                    setExemptFromEntitlementCheck.invoke(it, exemptFromEntitlementCheck)
                    setShouldShowEntitlementUi.invoke(it, shouldShowEntitlementUi)
                    build.invoke(it)
                }
            }
        }
    }

    private fun createTetheringRequestParcel(
        exemptFromEntitlementCheck: Boolean = true,
        shouldShowEntitlementUi: Boolean = false
    ): Any {
        return getRequestParcel(
            createTetheringRequest(
                exemptFromEntitlementCheck,
                shouldShowEntitlementUi
            )
        )
    }

    private fun getRequestParcel(request: Any): Any {
        return Class.forName("android.net.TetheringManager\$TetheringRequest").run {
            val getParcel = getDeclaredMethod("getParcel")
            getParcel.invoke(request)
        }
    }
}