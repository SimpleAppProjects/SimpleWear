package com.thewizrd.simplewear.services

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

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
}