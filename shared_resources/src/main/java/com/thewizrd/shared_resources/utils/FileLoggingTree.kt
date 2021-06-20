package com.thewizrd.shared_resources.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@SuppressLint("LogNotTimber")
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

            val today = LocalDateTime.now(ZoneOffset.UTC)

            val dateTimeStamp = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))
            val logTimeStamp =
                today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS", Locale.ROOT))

            val logNameFormat = "Logger.%s.log"
            val fileName = String.format(Locale.ROOT, logNameFormat, dateTimeStamp)

            val file = File(directory.path + File.separator + fileName)

            if (!file.exists())
                file.createNewFile()

            if (file.exists()) {
                val fileOutputStream = FileOutputStream(file, true)

                val priorityTAG = when (priority) {
                    Log.DEBUG -> "DEBUG"
                    Log.INFO -> "INFO"
                    Log.VERBOSE -> "VERBOSE"
                    Log.WARN -> "WARN"
                    Log.ERROR -> "ERROR"
                    else -> "DEBUG"
                }

                if (t != null) {
                    fileOutputStream.write("$logTimeStamp|$priorityTAG|${if (tag == null) "" else "$tag|"}$message\n$t\n".toByteArray())
                } else {
                    fileOutputStream.write("$logTimeStamp|$priorityTAG|${if (tag == null) "" else "$tag|"}$message\n".toByteArray())
                }

                fileOutputStream.close()
            }

            // Cleanup old logs if they exist
            if (!ranCleanup) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        // Only keep a weeks worth of logs
                        val daysToKeep = 7

                        // Get todays date
                        val todayLocal = today.toLocalDate()

                        // Create a list of the last 7 day's dates
                        val dateStampsToKeep: MutableList<String> = ArrayList()
                        for (i in 0 until daysToKeep) {
                            val date = todayLocal.minusDays(i.toLong())
                            val dateStamp =
                                date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))

                            dateStampsToKeep.add(
                                String.format(
                                    Locale.ROOT,
                                    logNameFormat,
                                    dateStamp
                                )
                            )
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