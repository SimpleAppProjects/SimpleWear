package com.thewizrd.simplewear.services

import android.Manifest
import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.App
import com.thewizrd.simplewear.PhoneBroadcastReceiver
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.wearable.WearableWorker

class TorchService : Service() {
    companion object {
        private const val JOB_ID = 1001
        private const val NOT_CHANNEL_ID = "SimpleWear.torchservice"

        const val ACTION_START_LIGHT = "SimpleWear.Droid.action.START_LIGHT"
        const val ACTION_END_LIGHT = "SimpleWear.Droid.action.END_LIGHT"

        fun enqueueWork(context: Context, work: Intent?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(work)
            } else {
                context.startService(work)
            }
        }

        private fun initChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Gets an instance of the NotificationManager service
                val context = App.instance!!.appContext
                val mNotifyMgr = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                var mChannel = mNotifyMgr.getNotificationChannel(NOT_CHANNEL_ID)
                if (mChannel == null) {
                    val notchannel_name = context.getString(R.string.not_channel_name_torch)
                    mChannel = NotificationChannel(NOT_CHANNEL_ID, notchannel_name, NotificationManager.IMPORTANCE_LOW)
                    // Configure the notification channel.
                    mChannel.setShowBadge(false)
                    mChannel.enableLights(false)
                    mChannel.enableVibration(false)
                    mNotifyMgr.createNotificationChannel(mChannel)
                }
            }
        }

        @get:TargetApi(Build.VERSION_CODES.O)
        private val foregroundNotification: Notification
            private get() {
                val context = App.instance!!.appContext
                val mBuilder = NotificationCompat.Builder(context, NOT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_lightbulb_outline_white_24dp)
                        .setContentTitle(context.getString(R.string.action_torch))
                        .setContentText(context.getString(R.string.not_torch_turnoff_summary))
                        .setContentIntent(PendingIntent.getBroadcast(context, JOB_ID,
                                Intent(context, PhoneBroadcastReceiver::class.java).setAction(ACTION_END_LIGHT), PendingIntent.FLAG_UPDATE_CURRENT))
                        .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                return mBuilder.build()
            }
    }

    private var mFlashEnabled = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
        }
        startForeground(JOB_ID, foregroundNotification)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground(JOB_ID, foregroundNotification)
        if (ACTION_START_LIGHT == intent.action) {
            turnOnFlashLight()
        } else if (ACTION_END_LIGHT == intent.action) {
            turnOffFlashLight()
            stopSelf()
        }
        WearableWorker.sendActionUpdate(applicationContext, Actions.TORCH)
        return START_STICKY
    }

    override fun onDestroy() {
        if (mFlashEnabled) {
            turnOffFlashLight()
        }
        stopForeground(true)
        super.onDestroy()
    }

    private fun turnOnFlashLight() {
        val hasCameraFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val isEnabled = (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)

        if (!hasCameraFlash || !isEnabled) {
            stopSelf()
            return
        }

        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, true)
            mFlashEnabled = true
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    private fun turnOffFlashLight() {
        val hasCameraFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val isEnabled = (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)

        if (!hasCameraFlash || !isEnabled) {
            stopSelf()
            return
        }

        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null

        try {
            cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, false)
            mFlashEnabled = false
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }
}