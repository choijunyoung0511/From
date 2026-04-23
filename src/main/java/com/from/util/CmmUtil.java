package com.from.util;

/**
 * 공통 유틸리티 클래스.
 * null 및 공백 처리 등 반복적으로 사용되는 헬퍼 메서드를 제공한다.
 */
public class CmmUtil {

    /**
     * null이면 빈 문자열을 반환하고, 아니면 trim()한 값을 반환한다.
     * HttpServletRequest.getParameter()가 반환하는 null을 안전하게 처리하기 위해 사용한다.
     *
     * @param str 검사할 문자열
     * @return null이면 "", 아니면 str.trim()
     */
    public static String nvl(String str) {
        return (str == null) ? "" : str.trim();
    }

    /**
     * null이거나 빈 문자열이면 defaultStr을 반환하고, 아니면 trim()한 값을 반환한다.
     *
     * @param str        검사할 문자열
     * @param defaultStr null 또는 빈 문자열일 때 반환할 기본값
     * @return str이 비어 있으면 defaultStr, 아니면 str.trim()
     */
    public static String nvl(String str, String defaultStr) {
        return (str == null || str.trim().isEmpty()) ? defaultStr : str.trim();
    }
}