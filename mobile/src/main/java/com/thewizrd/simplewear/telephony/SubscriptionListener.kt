package com.thewizrd.simplewear.telephony

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.PermissionChecker
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.App
import com.thewizrd.simplewear.wearable.WearableWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

object SubscriptionListener {
    private val subMap by lazy {
        mutableMapOf<Int, ContentObserver>()
    }

    var isRegistered = false
        private set

    private val listener = lazy {
        object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                updateActiveSubscriptions()
            }
        }
    }

    fun registerListener(context: Context): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            val subMgr = appContext.getSystemService(SubscriptionManager::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                subMgr.addOnSubscriptionsChangedListener(
                    Executors.newSingleThreadExecutor(),
                    listener.value
                )
            } else {
                subMgr.addOnSubscriptionsChangedListener(listener.value)
            }

            GlobalScope.launch(Dispatchers.Default) {
                listener.value.onSubscriptionsChanged()
            }

            true
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            false
        }.apply {
            isRegistered = this
        }
    }

    private fun updateActiveSubscriptions() {
        runCatching {
            val appContext = App.instance.appContext
            val subMgr = appContext.getSystemService(SubscriptionManager::class.java)

            if (PermissionChecker.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_PHONE_STATE
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                unregisterListener(appContext)
                return
            }

            // Get active SIMs (subscriptions)
            val subList = subMgr.activeSubscriptionInfoList
            val activeSubIds = subList.map { it.subscriptionId }

            // Remove any subs which are no longer active
            val entriesToRemove = subMap.filterNot {
                activeSubIds.contains(it.key)
            }

            entriesToRemove.forEach { (id, obs) ->
                subMap.remove(id)
                appContext.applicationContext.contentResolver.unregisterContentObserver(obs)
            }

            // Register any new subs
            subMgr.activeSubscriptionInfoList.forEach {
                if (it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    if (!subMap.containsKey(it.subscriptionId)) {
                        // Register listener for mobile data setting
                        val setting = Settings.Global.getUriFor("mobile_data${it.subscriptionId}")
                        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                            override fun onChange(selfChange: Boolean, uri: Uri?) {
                                super.onChange(selfChange, uri)
                                if (uri.toString().contains("mobile_data")) {
                                    WearableWorker.sendActionUpdate(appContext, Actions.MOBILEDATA)
                                }
                            }
                        }
                        appContext.contentResolver.registerContentObserver(setting, false, observer)
                        subMap[it.subscriptionId] = observer
                    }
                }
            }
        }
    }

    private fun unregisterLister() {
        unregisterListener(App.instance.appContext)
    }

    fun unregisterListener(context: Context) {
        runCatching {
            val appContext = context.applicationContext

            subMap.values.forEach {
                appContext.contentResolver.unregisterContentObserver(it)
            }

            subMap.clear()

            if (listener.isInitialized()) {
                val subMgr = appContext.getSystemService(SubscriptionManager::class.java)
                subMgr.removeOnSubscriptionsChangedListener(listener.value)
            }
        }

        isRegistered = false
    }
}