package com.thewizrd.simplewear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thewizrd.shared_resources.utils.FileUtils.deleteDirectory
import com.thewizrd.simplewear.wearable.WearableWorker
import com.thewizrd.simplewear.wearable.WearableWorker.Companion.enqueueAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun wearableServiceSpam() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        GlobalScope.launch {
            for (i in 0..7) {
                enqueueAction(appContext, WearableWorker.ACTION_SENDWIFIUPDATE)
                enqueueAction(appContext, WearableWorker.ACTION_SENDBTUPDATE)
                enqueueAction(appContext, "")
                enqueueAction(appContext, WearableWorker.ACTION_SENDBTUPDATE)
                enqueueAction(appContext, WearableWorker.ACTION_SENDWIFIUPDATE)
            }

            delay(7500)
        }
    }

    @Test
    fun supportedMusicPlayers() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val infos = appContext.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), PackageManager.GET_RESOLVED_FILTER)
        for (info in infos) {
            GlobalScope.launch(Dispatchers.Main) {
                val appInfo = info.activityInfo.applicationInfo
                val label = appContext.packageManager.getApplicationLabel(appInfo).toString()
                val appIntent = Intent()
                appIntent
                        .setAction(Intent.ACTION_VIEW)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).component = ComponentName(appInfo.packageName, info.activityInfo.name)
                appContext.startActivity(appIntent)

                delay(500)

                val i = Intent("com.android.music.musicservicecommand")
                i.putExtra("command", "play")
                appContext.sendBroadcast(i)
            }

            // First one only
            break
        }
    }

    // Context of the app under test.
    @Test
    fun isMusicActive() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        Assert.assertTrue(audioManager.isMusicActive)
    }

    @Test
    @Throws(IOException::class)
    fun logCleanupTest() {
        GlobalScope.launch(Dispatchers.IO) {
            // Context of the app under test.
            val appContext = ApplicationProvider.getApplicationContext<Context>()
            val filePath = appContext.getExternalFilesDir(null).toString() + "/logs"
            val directory = File(filePath)
            if (!directory.exists()) {
                Assert.assertTrue(directory.mkdir())
            }
            for (i in 0..3) {
                val file = File(filePath + File.separator + "Log." + i + ".log")
                Assert.assertTrue(file.createNewFile())
            }
            Assert.assertTrue(deleteDirectory(filePath))
        }
    }
}