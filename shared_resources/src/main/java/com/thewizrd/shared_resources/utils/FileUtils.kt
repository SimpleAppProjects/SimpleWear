package com.thewizrd.shared_resources.utils

import android.util.Log
import androidx.core.util.AtomicFile
import com.thewizrd.shared_resources.tasks.AsyncTask
import com.thewizrd.shared_resources.utils.Logger.writeLine
import java.io.*

object FileUtils {
    fun isValid(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && file.length() > 0
    }

    fun readFile(file: File): String? {
        return AsyncTask.await<String> {
            val mFile = AtomicFile(file)

            while (isFileLocked(file)) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
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
                writeLine(Log.ERROR, ex)
            } finally {
                // Close stream
                try {
                    reader?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            data
        }
    }

    fun writeToFile(data: String, file: File) {
        AsyncTask.run {
            val mFile = AtomicFile(file)

            while (isFileLocked(file)) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            var outputStream: FileOutputStream? = null
            var writer: OutputStreamWriter? = null

            try {
                outputStream = mFile.startWrite()
                writer = OutputStreamWriter(outputStream)

                // Clear file before writing
                //outputStream.SetLength(0);
                // TODOnevermind: async write and flush
                writer.write(data)
                writer.flush()
                mFile.finishWrite(outputStream)
            } catch (ex: IOException) {
                writeLine(Log.ERROR, ex)
            } finally {
                try {
                    writer?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteDirectory(path: String): Boolean {
        return AsyncTask.await<Boolean> {
            var success = false
            val directory = File(path)

            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        while (isFileLocked(file)) {
                            try {
                                Thread.sleep(100)
                            } catch (ignored: InterruptedException) {
                            }
                        }

                        file.delete()
                    }
                }

                success = directory.delete()
            }

            success
        }
    }

    fun isFileLocked(file: File): Boolean {
        if (!file.exists()) return false

        var stream: FileInputStream? = null
        stream = try {
            FileInputStream(file)
        } catch (fex: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            //the file is unavailable because it is:
            //still being written to
            //or being processed by another thread
            //or does not exist (has already been processed)
            return true
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        //file is not locked
        return false
    }
}