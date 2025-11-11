package com.thewizrd.simplewear.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import com.thewizrd.shared_resources.utils.Logger

/**
 * A notification listener service to allows us to grab active media sessions from their
 * notifications.
 * This class is only used on API 21+ because the Android media framework added getActiveSessions
 * in API 21.
 */
class NotificationListener : NotificationListenerService() {
    // Helper method to check if our notification listener is enabled. In order to get active media
    // sessions, we need an enabled notification listener component.
    companion object {
        fun isEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val notMgr = context.getSystemService(NotificationManager::class.java)
                return notMgr.isNotificationListenerAccessGranted(getComponentName(context))
            } else {
                return NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, NotificationListener::class.java)
        }
    }

    data class CallNotificationData(
        val key: String,
        val callType: Int?,
        val callerName: String?,
        val callerPhotoIcon: Icon? = null,
        val notifWhen: Long = 0
    ) {
        val callState: Int?
            get() = when (callType) {
                Notification.CallStyle.CALL_TYPE_INCOMING -> TelephonyManager.CALL_STATE_RINGING
                Notification.CallStyle.CALL_TYPE_ONGOING -> TelephonyManager.CALL_STATE_OFFHOOK
                null -> null
                else -> TelephonyManager.CALL_STATE_IDLE
            }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.isOngoing && sbn.notification.category == Notification.CATEGORY_CALL) {
            val callType = sbn.notification.extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
            val person = BundleCompat.getParcelable(
                sbn.notification.extras,
                Notification.EXTRA_CALL_PERSON,
                Person::class.java
            )
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)

            Logger.debug("NotificationListener", "call notification rec'vd")
            Logger.debug("NotificationListener", "key = ${sbn.key}")
            Logger.debug("NotificationListener", "callType = $callType")
            Logger.debug("NotificationListener", "person = ${person?.name}")
            Logger.debug("NotificationListener", "title = $title")

            val data = CallNotificationData(
                key = sbn.key,
                callType = if (callType >= 0) callType else null,
                callerName = person?.name?.toString() ?: title,
                callerPhotoIcon = person?.icon ?: sbn.notification.getLargeIcon(),
                notifWhen = sbn.notification.`when`
            )

            val existingIndex = OngoingCall.callNotifications.indexOfLast { it.key == sbn.key }
            if (existingIndex < 0) {
                OngoingCall.callNotifications.add(data)
            } else {
                OngoingCall.callNotifications[existingIndex] = data
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.isOngoing && sbn.notification.category == Notification.CATEGORY_CALL) {
            val callType = sbn.notification.extras.getInt(Notification.EXTRA_CALL_TYPE, -1)
            val person = BundleCompat.getParcelable(
                sbn.notification.extras,
                Notification.EXTRA_CALL_PERSON,
                Person::class.java
            )
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)

            Logger.debug("NotificationListener", "call notification removed")
            Logger.debug("NotificationListener", "removed key = ${sbn.key}")
            Logger.debug("NotificationListener", "removed callType = $callType")
            Logger.debug("NotificationListener", "removed person = ${person?.name}")
            Logger.debug("NotificationListener", "removed title = $title")


            OngoingCall.callNotifications.removeIf { it.key == sbn.key }
        }
    }
}