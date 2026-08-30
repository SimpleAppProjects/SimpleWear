package com.thewizrd.shared_resources.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.thewizrd.shared_resources.BuildConfig
import com.thewizrd.shared_resources.utils.Logger.DEBUG_MODE_ENABLED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.Writer
import java.lang.System.lineSeparator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

@SuppressLint("LogNotTimber")
class FileLoggingTree(context: Context) : Timber.Tree(), Closeable {
    companion object {
        private val TAG = FileLoggingTree::class.java.simpleName
        private const val DAYS_TO_KEEP = 7
        private const val LOG_NAME_FORMAT = "Logger.%s.log"
    }

    private val scope =
        CoroutineScope(Job() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val logDirectory = File(context.getExternalFilesDir(null).toString() + "/logs")

    private var logFile: File
    private var fileWriter: Writer

    init {
        if (!logDirectory.exists()) {
            logDirectory.mkdir()
        }

        logFile = createLogFile(LocalDateTime.now(ZoneOffset.UTC))
        fileWriter = FileWriter(logFile, true)
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return if (BuildConfig.DEBUG || DEBUG_MODE_ENABLED) {
            true
        } else {
            priority > Log.DEBUG
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        scope.launch {
            logEntry(priority, tag, message, t)
        }
    }

    private fun logEntry(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            if (!logDirectory.exists()) {
                logDirectory.mkdir()
            }

            val today = LocalDateTime.now(ZoneOffset.UTC)

            // Rotate log file if needed
            rotateLogFileIfNeeded(today)
            // Write to logs
            writeLogMessage(today, priority, tag, message)
            // Cleanup logs
            cleanupLogs()
        } catch (e: Exception) {
            Log.e(TAG, "Error while logging into file", e)
        }
    }

    private fun writeLogMessage(
        date: LocalDateTime,
        priority: Int,
        tag: String? = null,
        message: String
    ) {
        val priorityTAG = when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "DEBUG"
        }

        val logTimeStamp =
            date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS", Locale.ROOT))
        fileWriter.write("$logTimeStamp|$priorityTAG|${if (tag == null) "" else "$tag|"}$message")
        fileWriter.write(lineSeparator())

        if (priority == Log.ERROR) {
            fileWriter.flush()
        }
    }

    private fun cleanupLogs() {
        val existingFiles = logDirectory.listFiles { dir, name ->
            name.startsWith("Logger")
        } ?: emptyArray<File>()

        // Cleanup old logs if they exist
        if (existingFiles.size > DAYS_TO_KEEP) {
            runCatching {
                // Get today's date
                val todayLocal = LocalDate.now(ZoneOffset.UTC)

                // Create a list of the last 7 day's dates
                val dateStampsToKeep = mutableListOf<String>()
                for (i in 0 until DAYS_TO_KEEP) {
                    val date = todayLocal.minusDays(i.toLong())
                    val dateStamp =
                        date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))

                    dateStampsToKeep.add(String.format(Locale.ROOT, LOG_NAME_FORMAT, dateStamp))
                }

                // List all log files not in the above list
                val logs = existingFiles.filterNot { f -> dateStampsToKeep.contains(f.name) }

                // Delete all log files in the array above
                for (logToDel in logs) {
                    logToDel.delete()
                }
            }
        }
    }

    private fun rotateLogFileIfNeeded(date: LocalDateTime) {
        val logFileName = getLogFileName(date)

        if (logFile.name != logFileName) {
            close()
            logFile = createLogFile(LocalDateTime.now(ZoneOffset.UTC))
            fileWriter = FileOutputStream(logFile, true).writer()
        }
    }

    private fun createLogFile(date: LocalDateTime): File = createLogFile(getLogFileName(date))

    private fun createLogFile(logFileName: String): File {
        val file = File(logDirectory.path + File.separator + logFileName)

        if (!file.exists()) {
            file.createNewFile()
        }

        return file
    }

    private fun getLogFileName(date: LocalDateTime): String {
        val dateStamp = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))
        val fileName = String.format(Locale.ROOT, LOG_NAME_FORMAT, dateStamp)

        return fileName
    }

    override fun close() {
        runCatching {
            fileWriter.close()
        }
    }
}