package com.thewizrd.shared_resources.utils;

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
}
