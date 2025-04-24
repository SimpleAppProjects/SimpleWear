package com.thewizrd.shared_resources.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.util.AtomicFile
import androidx.core.util.ObjectsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object FileUtils {
    @JvmStatic
    fun isValid(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && file.length() > 0
    }

    @JvmStatic
    fun isValid(context: Context, assetUri: Uri?): Boolean {
        if (assetUri != null && ObjectsCompat.equals(assetUri.scheme, "file")) {
            var path = assetUri.path
            if (path?.startsWith("/android_asset") == true) {
                val startAsset = path.indexOf("/android_asset/")
                path = path.substring(startAsset + 15)

                var stream: InputStream? = null
                try {
                    stream = context.resources.assets.open(path)
                    return true
                } catch (ignored: IOException) {
                } finally {
                    stream?.closeQuietly()
                }
            } else if (path != null) {
                return File(path).exists()
            }
        }

        return false
    }

    suspend fun readFile(file: File): String? = withContext(Dispatchers.IO) {
        val mFile = AtomicFile(file)

        while (isFileLocked(file)) {
            supervisorScope {
                delay(100)
            }
        }

        var reader: BufferedReader? = null
        var data: String? = null

        try {
            reader = BufferedReader(InputStreamReader(mFile.openRead()))

            var line = reader.readLine()
            val sBuilder = StringBuilder()

            while (line != null) {
                sBuilder.append(line).append("\n")
                line = reader.readLine()
            }

            data = sBuilder.toString()
        } catch (ex: IOException) {
            Logger.writeLine(Log.ERROR, ex)
        } finally {
            // Close stream
            reader?.closeQuietly()
        }

        data
    }

    suspend fun writeToFile(data: String?, file: File) = withContext(Dispatchers.IO) {
        val mFile = AtomicFile(file)

        while (isFileLocked(file)) {
            supervisorScope {
                delay(100)
            }
        }

        var outputStream: FileOutputStream? = null
        var writer: OutputStreamWriter? = null

        try {
            outputStream = mFile.startWrite()
            writer = OutputStreamWriter(outputStream)

            writer.write(data)
            writer.flush()
            mFile.finishWrite(outputStream)
        } catch (ex: IOException) {
            Logger.writeLine(Log.ERROR, ex)
        } finally {
            writer?.closeQuietly()
            outputStream?.closeQuietly()
        }
    }

    suspend fun deleteDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        var success = false
        val directory = File(path)

        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    while (isFileLocked(file)) {
                        supervisorScope {
                            delay(100)
                        }
                    }

                    file.delete()
                }
            }

            success = directory.delete()
        }

        success
    }

    @JvmStatic
    @WorkerThread
    fun isFileLocked(file: File): Boolean {
        if (!file.exists())
            return false

        var stream: FileInputStream? = null

        try {
            stream = FileInputStream(file)
        } catch (fex: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            //the file is unavailable because it is:
            //still being written to
            //or being processed by another thread
            //or does not exist (has already been processed)
            return true
        } finally {
            stream?.closeQuietly()
        }

        //file is not locked
        return false
    }
}

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: RuntimeException) {
        throw e
    } catch (_: Exception) {
    }
}