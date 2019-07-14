package com.thewizrd.shared_resources.utils;

import android.util.Log;

import androidx.core.util.AtomicFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.NormalAction;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.ValueAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;

public class JSONParser {

    private static Gson gson;

    static {
        gson = new GsonBuilder()
                .registerTypeAdapterFactory(
                        RuntimeTypeAdapterFactory.of(Action.class)
                                .registerSubtype(ToggleAction.class)
                                .registerSubtype(ValueAction.class)
                                .registerSubtype(NormalAction.class))
                .setDateFormat("dd.MM.yyyy HH:mm:ss ZZZZZ")
                .serializeNulls().create();
    }

    public static <T> T deserializer(String response, Type type) {
        T object = null;

        try {
            object = gson.fromJson(response, type);
        } catch (Exception ex) {
            Logger.writeLine(Log.ERROR, ex);
        }

        return object;
    }

    public static <T> T deserializer(String response, Class<T> obj) {
        T object = null;

        try {
            object = gson.fromJson(response, obj);
        } catch (Exception ex) {
            Logger.writeLine(Log.ERROR, ex);
        }

        return object;
    }

    public static <T> T deserializer(InputStream stream, Type type) {
        T object = null;
        InputStreamReader sReader = null;
        JsonReader reader = null;

        try {
            sReader = new InputStreamReader(stream);
            reader = new JsonReader(sReader);

            object = gson.fromJson(reader, type);
        } catch (Exception ex) {
            Logger.writeLine(Log.ERROR, ex);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                //e.printStackTrace();
            }
            try {
                if (sReader != null) {
                    sReader.close();
                }
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }

        return object;
    }

    public static <T> T deserializer(File file, Type type) {
        while (FileUtils.isFileLocked(file)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        T object = null;
        FileInputStream stream = null;
        InputStreamReader sReader = null;
        JsonReader reader = null;

        AtomicFile mFile = new AtomicFile(file);
        try {
            stream = mFile.openRead();
            sReader = new InputStreamReader(stream);
            reader = new JsonReader(sReader);

            object = gson.fromJson(reader, type);
        } catch (Exception ex) {
            Logger.writeLine(Log.ERROR, ex);
            object = null;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (sReader != null) {
                    sReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return object;
    }

    public static void serializer(Object object, File file) {
        // Wait for file to be free
        while (FileUtils.isFileLocked(file)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        AtomicFile mFile = new AtomicFile(file);
        FileOutputStream stream = null;
        JsonWriter writer = null;

        try {
            stream = mFile.startWrite();

            writer = new JsonWriter(new OutputStreamWriter(stream));

            gson.toJson(object, object.getClass(), writer);
            writer.flush();
            mFile.finishWrite(stream);
            //FileUtils.writeToFile(gson.toJson(object), file);
        } catch (IOException ex) {
            Logger.writeLine(Log.ERROR, ex);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String serializer(Object object, Type type) {
        return gson.toJson(object, type);
    }
}

