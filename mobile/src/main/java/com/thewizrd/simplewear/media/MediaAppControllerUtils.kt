package com.thewizrd.simplewear.media

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import java.util.*

object MediaAppControllerUtils {
    fun getActiveMediaSessions(
        context: Context,
        listenerComponent: ComponentName
    ): List<MediaController> {
        val mMediaSessionManager = context.getSystemService(MediaSessionManager::class.java)
        return mMediaSessionManager.getActiveSessions(listenerComponent)
    }

    fun getMediaAppsFromControllers(
        context: Context,
        controllers: Collection<MediaController>,
    ): List<ApplicationInfo> {
        val mediaApps = ArrayList<ApplicationInfo>()
        for (controller in controllers) {
            val packageName = controller.packageName
            val info: ApplicationInfo
            try {
                info = context.packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // This should not happen. If we get a media session for a package, then the
                // package must be installed on the device.
                Log.e(ContentValues.TAG, "Unable to load package details", e)
                continue
            }

            mediaApps.add(info)
        }
        return mediaApps
    }

    fun getMediaAppsFromControllers(
        controllers: Collection<MediaController>,
        packageManager: PackageManager,
        resources: Resources
    ): List<MediaAppDetails> {
        val mediaApps = ArrayList<MediaAppDetails>()
        for (controller in controllers) {
            val packageName = controller.packageName
            val info: ApplicationInfo
            try {
                info = packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // This should not happen. If we get a media session for a package, then the
                // package must be installed on the device.
                Log.e(ContentValues.TAG, "Unable to load package details", e)
                continue
            }

            mediaApps.add(
                MediaAppDetails(info, packageManager, resources, controller.sessionToken)
            )
        }
        return mediaApps
    }

    fun isMediaActive(
        context: Context,
        listenerComponent: ComponentName
    ): Boolean {
        val mMediaSessionManager = context.getSystemService(MediaSessionManager::class.java)
        val activeSessions = mMediaSessionManager.getActiveSessions(listenerComponent)
        return activeSessions.any {
            it.playbackState != null &&
                    (it.playbackState!!.state == PlaybackStateCompat.STATE_PLAYING
                            || it.playbackState!!.state != PlaybackStateCompat.STATE_NONE)
        }
    }
}