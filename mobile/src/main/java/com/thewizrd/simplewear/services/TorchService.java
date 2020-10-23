package com.thewizrd.simplewear.services;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.App;
import com.thewizrd.simplewear.PhoneBroadcastReceiver;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.wearable.WearableWorker;

public class TorchService extends Service {

    private static final int JOB_ID = 1001;
    private static final String NOT_CHANNEL_ID = "SimpleWear.torchservice";

    public static final String ACTION_START_LIGHT = "SimpleWear.Droid.action.START_LIGHT";
    public static final String ACTION_END_LIGHT = "SimpleWear.Droid.action.END_LIGHT";

    private boolean mFlashEnabled = false;

    public static void enqueueWork(Context context, Intent work) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(work);
        } else {
            context.startService(work);
        }
    }

    private static void initChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Gets an instance of the NotificationManager service
            Context context = App.getInstance().getAppContext();
            NotificationManager mNotifyMgr = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel mChannel = mNotifyMgr.getNotificationChannel(NOT_CHANNEL_ID);

            if (mChannel == null) {
                String notchannel_name = context.getString(R.string.not_channel_name_torch);

                mChannel = new NotificationChannel(NOT_CHANNEL_ID, notchannel_name, NotificationManager.IMPORTANCE_LOW);
                // Configure the notification channel.
                mChannel.setShowBadge(false);
                mChannel.enableLights(false);
                mChannel.enableVibration(false);
                mNotifyMgr.createNotificationChannel(mChannel);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static Notification getForegroundNotification() {
        Context context = App.getInstance().getAppContext();
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, NOT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_lightbulb_outline_white_24dp)
                        .setContentTitle(context.getString(R.string.action_torch))
                        .setContentText(context.getString(R.string.not_torch_turnoff_summary))
                        .setContentIntent(PendingIntent.getBroadcast(context, JOB_ID,
                                new Intent(context, PhoneBroadcastReceiver.class).setAction(ACTION_END_LIGHT), PendingIntent.FLAG_UPDATE_CURRENT))
                        .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        return mBuilder.build();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel();
        }

        startForeground(JOB_ID, getForegroundNotification());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(JOB_ID, getForegroundNotification());

        if (intent != null && ACTION_START_LIGHT.equals(intent.getAction())) {
            turnOnFlashLight();
        } else if (intent != null && ACTION_END_LIGHT.equals(intent.getAction())) {
            turnOffFlashLight();
            stopSelf();
        }

        WearableWorker.sendActionUpdate(getApplicationContext(), Actions.TORCH);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mFlashEnabled) {
            turnOffFlashLight();
        }

        stopForeground(true);

        super.onDestroy();
    }

    private void turnOnFlashLight() {
        final boolean hasCameraFlash = getPackageManager().
                hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        boolean isEnabled = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasCameraFlash || !isEnabled) {
            stopSelf();
            return;
        }

        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = cameraManager.getCameraIdList()[0];
            cameraManager.setTorchMode(cameraId, true);
            mFlashEnabled = true;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }

    private void turnOffFlashLight() {
        final boolean hasCameraFlash = getPackageManager().
                hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        boolean isEnabled = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasCameraFlash || !isEnabled) {
            stopSelf();
            return;
        }

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            cameraId = cameraManager.getCameraIdList()[0];
            cameraManager.setTorchMode(cameraId, false);
            mFlashEnabled = false;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }
}
