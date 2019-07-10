package com.thewizrd.shared_resources.utils;

import android.util.Log;

import androidx.core.util.AtomicFile;

import com.thewizrd.shared_resources.AsyncTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.Callable;

public class FileUtils {
    public static boolean isValid(String filePath) {
        File file = new File(filePath);

        return file.exists() && file.length() > 0;
    }

    public static String readFile(final File file) {
        return new AsyncTask<String>().await(new Callable<String>() {
            @Override
            public String call() throws Exception {
                AtomicFile mFile = new AtomicFile(file);

                while (isFileLocked(file)) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                BufferedReader reader = null;
                String data = null;

                try {
                    reader = new BufferedReader(new InputStreamReader(mFile.openRead()));

                    String line = reader.readLine();
                    StringBuilder sBuilder = new StringBuilder();

                    while (line != null) {
                        sBuilder.append(line).append("\n");
                        line = reader.readLine();
                    }

                    data = sBuilder.toString();
                } catch (IOException ex) {
                    Logger.writeLine(Log.ERROR, ex);
                } finally {
                    // Close stream
                    try {
                        if (reader != null) {
                            reader.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                return data;
            }
        });
    }

    public static void writeToFile(final String data, final File file) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                AtomicFile mFile = new AtomicFile(file);

                while (isFileLocked(file)) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                FileOutputStream outputStream = null;
                OutputStreamWriter writer = null;

                try {
                    outputStream = mFile.startWrite();
                    writer = new OutputStreamWriter(outputStream);

                    // Clear file before writing
                    //outputStream.SetLength(0);
                    // TODOnevermind: async write and flush
                    writer.write(data);
                    writer.flush();
                    mFile.finishWrite(outputStream);
                } catch (IOException ex) {
                    Logger.writeLine(Log.ERROR, ex);
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    public static boolean isFileLocked(File file) {
        if (!file.exists())
            return false;

        FileInputStream stream = null;

        try {
            stream = new FileInputStream(file);
        } catch (FileNotFoundException fex) {
            return false;
        } catch (IOException e) {
            //the file is unavailable because it is:
            //still being written to
            //or being processed by another thread
            //or does not exist (has already been processed)
            return true;
        } finally {
            if (stream != null)
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        //file is not locked
        return false;
    }
}
