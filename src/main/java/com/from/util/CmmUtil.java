package com.from.util;

public class CmmUtil {

    public static String nvl(String str) {
        return (str == null) ? "" : str.trim();
    }

    public static String nvl(String str, String defaultStr) {
        return (str == null || str.trim().isEmpty()) ? defaultStr : str.trim();
    }
}