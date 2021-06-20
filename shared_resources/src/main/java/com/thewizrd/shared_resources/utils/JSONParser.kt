package com.thewizrd.shared_resources.utils

import android.util.Log
import androidx.core.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.typeadapters.LowercaseEnumTypeAdapterFactory
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory
import com.thewizrd.shared_resources.actions.*
import java.io.*
import java.lang.reflect.Type

object JSONParser {
    private val gson: Gson = GsonBuilder()
            .registerTypeAdapterFactory(
                    RuntimeTypeAdapterFactory.of(Action::class.java)
                            .registerSubtype(ToggleAction::class.java)
                            .registerSubtype(ValueAction::class.java)
                            .registerSubtype(NormalAction::class.java)
                            .registerSubtype(MultiChoiceAction::class.java)
                            .registerSubtype(VolumeAction::class.java)
            )
            .registerTypeAdapterFactory(LowercaseEnumTypeAdapterFactory())
            .setDateFormat("dd.MM.yyyy HH:mm:ss ZZZZZ")
            .serializeNulls()
            .create()

    fun <T> deserializer(response: String?, type: Type?): T? {
        var `object`: T? = null

        try {
            `object` = gson.fromJson(response, type)
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex)
        }

        return `object`
    }

    fun <T> deserializer(response: String?, obj: Class<T>?): T? {
        var `object`: T? = null

        try {
            `object` = gson.fromJson(response, obj)
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex)
        }

        return `object`
    }

    fun <T> deserializer(stream: InputStream, type: Type?): T? {
        var `object`: T? = null
        var sReader: InputStreamReader? = null
        var reader: JsonReader? = null

        try {
            sReader = InputStreamReader(stream)
            reader = JsonReader(sReader)

            `object` = gson.fromJson(reader, type)
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex)
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) {
                //e.printStackTrace();
            }
            try {
                sReader?.close()
            } catch (e: IOException) {
                //e.printStackTrace();
            }
        }

        return `object`
    }

    fun <T> deserializer(file: File, type: Type?): T? {
        while (FileUtils.isFileLocked(file)) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        var `object`: T? = null
        var stream: FileInputStream? = null
        var sReader: InputStreamReader? = null
        var reader: JsonReader? = null

        val mFile = AtomicFile(file)
        try {
            stream = mFile.openRead()
            sReader = InputStreamReader(stream)
            reader = JsonReader(sReader)

            `object` = gson.fromJson(reader, type)
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex)
            `object` = null
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            try {
                sReader?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return `object`
    }

    fun serializer(`object`: Any, file: File) {
        // Wait for file to be free
        while (FileUtils.isFileLocked(file)) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        val mFile = AtomicFile(file)
        var stream: FileOutputStream? = null
        var writer: JsonWriter? = null

        try {
            stream = mFile.startWrite()

            writer = JsonWriter(OutputStreamWriter(stream))

            gson.toJson(`object`, `object`.javaClass, writer)
            writer.flush()
            mFile.finishWrite(stream)
            //FileUtils.writeToFile(gson.toJson(object), file);
        } catch (ex: IOException) {
            Logger.writeLine(Log.ERROR, ex)
        } finally {
            try {
                writer?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @JvmName("serializerOrNull")
    fun serializer(`object`: Any?, type: Type): String? {
        if (`object` == null) return null
        return serializer(`object`, type)
    }

    fun serializer(`object`: Any, type: Type): String {
        return gson.toJson(`object`, type)
    }
}