package com.from.util;


//널처리
public class CmmUtil {

    //널이면 빈 물자열 반환하고 아니면 trim 한 값을 반환
    public static String nvl(String str) {
        return (str == null) ? "" : str.trim();
    }

    public static String nvl(String str, String defaultStr) {
        return (str == null || str.trim().isEmpty()) ? defaultStr : str.trim();
    }
}