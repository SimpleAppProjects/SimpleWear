package com.thewizrd.shared_resources.utils;

public class StringUtils {
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static boolean isNullOrWhitespace(String s) {
        return s == null || isWhitespace(s);
    }

    private static boolean isWhitespace(String s) {
        if (s == null)
            return true;

        for (int idx = 0; idx < s.length(); ++idx) {
            if (!Character.isWhitespace(s.toCharArray()[idx]))
                return false;
        }

        return true;
    }

    public static String lineSeparator() {
        return System.lineSeparator();
    }
}
