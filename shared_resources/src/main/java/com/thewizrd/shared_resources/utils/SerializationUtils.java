package com.thewizrd.shared_resources.utils;

import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class SerializationUtils {
    public static byte[] booleanToBytes(boolean value) {
        return new byte[]{(byte) (value ? 1 : 0)};
    }

    public static boolean bytesToBool(byte[] data) {
        return !(data[0] == 0);
    }

    public static byte[] stringToBytes(String value) {
        return value.getBytes(Charset.forName("UTF-8"));
    }

    public static String bytesToString(byte[] data) {
        return new String(data);
    }

    public static byte[] intToBytes(int value) {
        ByteBuffer buf;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            buf = ByteBuffer.allocate(Integer.BYTES);
        } else {
            buf = ByteBuffer.allocate(4);
        }
        return buf.putInt(value).array();
    }

    public static int bytesToInt(byte[] data) {
        return ByteBuffer.wrap(data).getInt();
    }

    public static byte[] longToBytes(long value) {
        ByteBuffer buf;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            buf = ByteBuffer.allocate(Long.BYTES);
        } else {
            buf = ByteBuffer.allocate(8);
        }
        return buf.putLong(value).array();
    }

    public static long bytesToLong(byte[] data) {
        return ByteBuffer.wrap(data).getLong();
    }
}
