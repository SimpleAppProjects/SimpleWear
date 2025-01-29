package com.thewizrd.simplewear.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import androidx.annotation.DeprecatedSinceApi
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.android.dx.stock.ProxyBuilder
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isWriteSystemSettingsPermissionEnabled
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

object TetherHelper {
    private const val TAG = "TetherHelper"

    /*
     * Wifi Tethering Methods
     *
     * Credit to the following:
     * https://github.com/aegis1980/WifiHotSpot
     * https://stackoverflow.com/a/52219887
     * https://github.com/C-D-Lewis/dashboard
     */
    private const val WIFI_AP_STATE_DISABLING = 10
    private const val WIFI_AP_STATE_DISABLED = 11
    private const val WIFI_AP_STATE_ENABLING = 12
    private const val WIFI_AP_STATE_ENABLED = 13
    private const val WIFI_AP_STATE_FAILED = 14

    fun getWifiApState(context: Context): Int {
        return runCatching {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_WIFI_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val wifiMan =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val getWifiApStateMethod = wifiMan.javaClass.getMethod("getWifiApState")

                val state = getWifiApStateMethod.invoke(wifiMan) as Int

                return state
            }

            return WIFI_AP_STATE_FAILED
        }.onFailure {
            Logger.error(TAG, it, "Error getting wifi AP state")
        }.getOrDefault(WIFI_AP_STATE_ENABLED)
    }

    fun isWifiApEnabled(context: Context): Boolean {
        val state = getWifiApState(context)

        return when (state) {
            WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_ENABLING -> true
            WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING -> false
            else -> {
                Logger.error(TAG, "Invalid Wifi AP state: $state")
                return false
            }
        }
    }

    private fun getWifiApConfiguration(context: Context): WifiConfiguration? {
        return runCatching {
            val wifiMan =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val getWifiApConfigurationMethod = wifiMan.javaClass.getMethod("getWifiApConfiguration")

            val config = getWifiApConfigurationMethod.invoke(wifiMan) as? WifiConfiguration?

            return config
        }.onFailure {
            Logger.error(TAG, it, "Error getting wifi AP config")
        }.getOrNull()
    }

    fun setWifiApEnabled(context: Context, enable: Boolean): ActionStatus {
        return runCatching {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                val wifiMan =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return if (isWriteSystemSettingsPermissionEnabled(context)) {
                        val retVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (enable) startTethering(context) else stopTethering(context)
                        } else {
                            if (enable) startTetheringPreR(context) else stopTetheringPreR(context)
                        }

                        if (retVal) ActionStatus.SUCCESS else ActionStatus.FAILURE
                    } else {
                        ActionStatus.PERMISSION_DENIED
                    }
                } else {
                    if (enable) {
                        // WiFi tethering requires WiFi to be off
                        wifiMan.isWifiEnabled = false
                    }

                    val setWifiApEnabledMethod = wifiMan.javaClass.getMethod(
                        "setWifiApEnabled",
                        WifiConfiguration::class.java,
                        Boolean::class.java
                    )
                    val retVal = setWifiApEnabledMethod.invoke(
                        wifiMan,
                        getWifiApConfiguration(context),
                        enable
                    ) as Boolean
                    return if (retVal) ActionStatus.SUCCESS else ActionStatus.FAILURE
                }
            }
            return ActionStatus.PERMISSION_DENIED
        }.onFailure {
            Logger.error(TAG, it, "Error setting wifi AP state")
        }.getOrElse {
            if (it is SecurityException || it.cause is SecurityException) {
                ActionStatus.PERMISSION_DENIED
            } else {
                ActionStatus.FAILURE
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    private fun isTetheringActivePreR(context: Context): Boolean {
        return runCatching {
            val cm =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val getTetheredIfacesMethod = cm.javaClass.getMethod("getTetheredIfaces")
            val resArr = getTetheredIfacesMethod.invoke(cm) as? Array<*>
            return !resArr.isNullOrEmpty() && resArr.any {
                it is String && (it.contains("wlan") || it.contains(
                    "softap"
                ))
            }
        }.onFailure {
            Logger.error(TAG, it, "Error getting tethering state")
        }.getOrDefault(false)
    }

    /*
     * android.net
     * ConnectivityManager / TetheringManager constants
     */
    /* TetheringType */
    private const val TETHERING_INVALID = -1
    private const val TETHERING_WIFI = 0
    private const val TETHERING_USB = 1
    private const val TETHERING_BLUETOOTH = 2

    /* TetheringManager service */
    private const val TETHERING_SERVICE = "tethering"

    /* Tether error codes */
    private const val TETHER_ERROR_NO_ERROR = 0
    private const val TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION = 14

    @RequiresApi(Build.VERSION_CODES.O)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    private fun startTetheringPreR(context: Context): Boolean {
        Logger.info(TAG, "entering startTetheringPreR...")

        return runCatching {
            if (isTetheringActivePreR(context)) {
                Logger.info(TAG, "tethering already enabled")
                return false
            }

            val codeCacheDir = context.applicationContext.codeCacheDir
            val proxy = try {
                ProxyBuilder.forClass(getConnMgrOnStartTetheringCallbackClass())
                    .dexCache(codeCacheDir)
                    .handler { proxy, method, args ->
                        when (method?.name) {
                            "onTetheringStarted" -> {
                                Logger.info("Proxy", "onTetheringStarted")
                            }

                            "onTetheringFailed" -> {
                                Logger.error(
                                    "Proxy",
                                    "onTetheringFailed: args = ${args.contentToString()}"
                                )
                            }

                            else -> {
                                ProxyBuilder.callSuper(proxy, method, args)
                            }
                        }

                        null
                    }.build()
            } catch (e: Exception) {
                Logger.error(TAG, e, "startTethering: Error ProxyBuilder")
                return@runCatching false
            }

            val cm =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val method = cm.javaClass.getMethod(
                "startTethering",
                Int::class.java, /* type */
                Boolean::class.java, /* showProvisioningUi */
                getConnMgrOnStartTetheringCallbackClass(),
                Handler::class.java
            )
            method.invoke(cm, TETHERING_WIFI, false, proxy, null)
            true
        }.getOrElse {
            if (it is SecurityException || it.cause is SecurityException) {
                Logger.error(TAG, it, "Permission denied starting tethering")
                throw it
            } else if (it is NoSuchMethodException) {
                Logger.error(TAG, "startTethering method is unavailable")
            } else {
                Logger.error(TAG, it, "Error starting tethering")
            }

            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    private fun stopTetheringPreR(context: Context): Boolean {
        Logger.info(TAG, "entering stopTetheringPreR...")

        return runCatching {
            val cm =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method = cm.javaClass.getMethod("stopTethering", Int::class.java)
            method.invoke(cm, TETHERING_WIFI)
            true
        }.getOrElse {
            if (it is SecurityException || it.cause is SecurityException) {
                Logger.error(TAG, it, "Permission denied stopping tethering")
                throw it
            } else if (it is NoSuchMethodException) {
                Logger.error(TAG, "stopTethering method is unavailable")
            } else {
                Logger.error(TAG, it, "Error stopping tethering")
            }

            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    private fun getConnMgrOnStartTetheringCallbackClass(): Class<*>? {
        return runCatching {
            Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")
        }.onFailure {
            Logger.error(TAG, it, "Error getting OnStartTetheringCallback class")
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun isTetheringActive(context: Context): Boolean {
        return runCatching {
            val getTetheredIfacesMethod = Class.forName("android.net.TetheringManager")
                .getMethod("getTetheredIfaces")
            val tetheringMgr = context.applicationContext.getSystemService(TETHERING_SERVICE)

            val resArr = getTetheredIfacesMethod.invoke(tetheringMgr) as? Array<*>
            return !resArr.isNullOrEmpty() && resArr.any {
                it is String && (it.contains("wlan") || it.contains("softap"))
            }
        }.getOrElse {
            Logger.error(TAG, it, "Error getting tethering state")
            // Fallback to ConnectivityManager
            isTetheringActivePreR(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startTethering(
        context: Context,
        exemptFromEntitlementCheck: Boolean = true,
        shouldShowEntitlementUi: Boolean = false
    ): Boolean {
        Logger.info(TAG, "entering startTethering...")

        return runCatching {
            if (isTetheringActive(context)) {
                Logger.info(TAG, "tethering already enabled")
                return false
            }

            val tetherCallbackIface = getTetherMgrStartTetheringCallbackInterface()
            val proxy = try {
                Proxy.newProxyInstance(
                    tetherCallbackIface.classLoader,
                    arrayOf(tetherCallbackIface)
                ) { _, method, args ->
                    when (method?.name) {
                        "onTetheringStarted" -> {
                            Logger.info("Proxy", "onTetheringStarted")
                        }

                        "onTetheringFailed" -> {
                            val resultCode = args[0] as Int

                            if (resultCode == TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION) {
                                // retry
                                startTethering(context, false, shouldShowEntitlementUi)
                            } else {
                                Logger.error("Proxy", "onTetheringFailed: code = $resultCode")
                            }
                        }
                    }

                    null
                }
            } catch (e: Exception) {
                Logger.error(TAG, e, "startTethering: Error Proxy")
                return@runCatching false
            }

            val tetheringMgr = context.applicationContext.getSystemService(TETHERING_SERVICE)
            val tetheringMgrClass = Class.forName("android.net.TetheringManager")

            val method = tetheringMgrClass.getMethod(
                "startTethering",
                Class.forName("android.net.TetheringManager\$TetheringRequest"), /* request */
                Executor::class.java,
                getTetherMgrStartTetheringCallbackInterface()
            )
            method.invoke(
                tetheringMgr,
                createTetheringRequest(exemptFromEntitlementCheck, shouldShowEntitlementUi),
                Executor { it.run() },
                proxy
            )
            true
        }.getOrElse {
            if (it is SecurityException || it.cause is SecurityException) {
                Logger.error(TAG, it, "Permission denied starting tethering")
                throw it
            } else if (it is NoSuchMethodException) {
                Logger.error(TAG, "startTethering method is unavailable")
            } else {
                Logger.error(TAG, it, "Error starting tethering")
            }

            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun stopTethering(context: Context): Boolean {
        Logger.info(TAG, "entering stopTethering...")

        return runCatching {
            val tetheringMgr = context.applicationContext.getSystemService(TETHERING_SERVICE)
            val tetheringMgrClass = Class.forName("android.net.TetheringManager")
            val method = tetheringMgrClass.getMethod("stopTethering", Int::class.java)
            method.invoke(tetheringMgr, TETHERING_WIFI)
            true
        }.getOrElse {
            if (it is SecurityException || it.cause is SecurityException) {
                Logger.error(TAG, it, "Permission denied stopping tethering")
                throw it
            } else if (it is NoSuchMethodException) {
                Logger.error(TAG, "stopTethering method is unavailable")
            } else {
                Logger.error(TAG, it, "Error stopping tethering")
            }

            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getTetherMgrStartTetheringCallbackInterface(): Class<*> {
        return runCatching {
            Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        }.onFailure {
            Logger.error(TAG, it, "Error getting StartTetheringCallback class")
        }.getOrThrow()
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
}