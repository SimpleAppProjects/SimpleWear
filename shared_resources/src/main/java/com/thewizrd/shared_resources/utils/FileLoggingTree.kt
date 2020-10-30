package com.thewizrd.shared_resources.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class FileLoggingTree(private val context: Context) : Timber.Tree() {
    companion object {
        private val TAG = FileLoggingTree::class.java.simpleName
        private var ranCleanup = false
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val directory = File(context.getExternalFilesDir(null).toString() + "/logs")
            if (!directory.exists()) {
                directory.mkdir()
            }
            val cal = Calendar.getInstance()
            val today = cal.time
            val dateTimeStamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(today)
            val logTimeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.ROOT).format(today)
            val logNameFormat = "Logger.%s.log"
            val fileName = String.format(Locale.ROOT, logNameFormat, dateTimeStamp)
            val file = File(directory.path + File.separator + fileName)
            if (!file.exists()) file.createNewFile()
            if (file.exists()) {
                val fileOutputStream: OutputStream = FileOutputStream(file, true)
                var priorityTAG: String? = null
                priorityTAG = when (priority) {
                    Log.DEBUG -> "DEBUG"
                    Log.INFO -> "INFO"
                    Log.VERBOSE -> "VERBOSE"
                    Log.WARN -> "WARN"
                    Log.ERROR -> "ERROR"
                    else -> "DEBUG"
                }
                if (t != null)
                    fileOutputStream.write((logTimeStamp + "|" + priorityTAG + "|" + (if (tag == null) "" else tag + "|") + message + "\n" + t.toString() + "\n").encodeToByteArray());
                else
                    fileOutputStream.write((logTimeStamp + "|" + priorityTAG + "|" + (if (tag == null) "" else tag + "|") + message + "\n").encodeToByteArray());

                fileOutputStream.close()
            }

            // Cleanup old logs if they exist
            if (!ranCleanup) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        // Only keep a weeks worth of logs
                        val daysToKeep = 7

                        // Create a list of the last 7 day's dates
                        val dateStampsToKeep: MutableList<String> = ArrayList()
                        for (i in 0 until daysToKeep) {
                            val date = Date(today.time - i * 24 * 60 * 60 * 1000)
                            val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date)
                            dateStampsToKeep.add(String.format(Locale.ROOT, logNameFormat, dateStamp))
                        }

                        // List all log files not in the above list
                        val logs = directory.listFiles { dir, name ->
                            name.startsWith("Logger") && !dateStampsToKeep.contains(name)
                        }

                        // Delete all log files in the array above
                        for (logToDel in logs) {
                            logToDel.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cleaning up log files : $e")
                    }
                }
                ranCleanup = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while logging into file : $e")
        }
    }
}