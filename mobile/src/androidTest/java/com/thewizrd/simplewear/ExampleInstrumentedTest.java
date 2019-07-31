package com.thewizrd.simplewear;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.simplewear.wearable.WearableDataListenerService;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void WearableServiceSpam() {
        // Context of the app under test.
        final Context appContext = ApplicationProvider.getApplicationContext();

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 8; i++) {
                    WearableDataListenerService.enqueueWork(appContext, new Intent(appContext, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDWIFIUPDATE));
                    WearableDataListenerService.enqueueWork(appContext, new Intent(appContext, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDBTUPDATE));
                    WearableDataListenerService.enqueueWork(appContext, new Intent(appContext, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDMOBILEDATAUPDATE));
                    WearableDataListenerService.enqueueWork(appContext, new Intent(appContext, WearableDataListenerService.class)
                            .setAction(""));
                    WearableDataListenerService.enqueueWork(appContext, new Intent(appContext, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDBTUPDATE));
                    WearableDataListenerService.enqueueWork(appContext, new Intent(appContext, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDWIFIUPDATE));
                    WearableDataListenerService.enqueueWork(appContext, new Intent(appContext, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDMOBILEDATAUPDATE));
                }
            }
        });

        new AsyncTask<Void>().await(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Thread.sleep(7500);
                return null;
            }
        });
    }

    @Test
    public void supportedMusicPlayers() {
        // Context of the app under test.
        final Context appContext = ApplicationProvider.getApplicationContext();

        List<ResolveInfo> infos = appContext.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), PackageManager.GET_RESOLVED_FILTER);

        for (final ResolveInfo info : infos) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                    String label = appContext.getPackageManager().getApplicationLabel(appInfo).toString();
                    Intent appIntent = new Intent();
                    appIntent
                            .setAction(Intent.ACTION_VIEW)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .setComponent(new ComponentName(appInfo.packageName, info.activityInfo.name));
                    appContext.startActivity(appIntent);

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Intent i = new Intent("com.android.music.musicservicecommand");
                    i.putExtra("command", "play");
                    appContext.sendBroadcast(i);
                }
            });

            // First one only
            break;
        }
    }

    @Test
    public void isMusicActive() {
        // Context of the app under test.
        final Context appContext = ApplicationProvider.getApplicationContext();

        AudioManager audioManager = appContext.getSystemService(AudioManager.class);
        Assert.assertTrue(audioManager.isMusicActive());
    }
}
