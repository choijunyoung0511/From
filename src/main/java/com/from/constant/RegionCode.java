package com.from.constant;

// 도서관 정보나루 API의 지역코드(region). 화면에는 지역명만 노출하고,
// libSrchByBook 등 API 호출 시 필요한 코드로 변환하는 용도로 한 곳에서만 관리한다
public enum RegionCode {

    SEOUL("서울", "11"),
    BUSAN("부산", "21"),
    DAEGU("대구", "22"),
    INCHEON("인천", "23"),
    GWANGJU("광주", "24"),
    DAEJEON("대전", "25"),
    ULSAN("울산", "26"),
    SEJONG("세종", "29"),
    GYEONGGI("경기", "31"),
    GANGWON("강원", "32"),
    CHUNGBUK("충북", "33"),
    CHUNGNAM("충남", "34"),
    JEONBUK("전북", "35"),
    JEONNAM("전남", "36"),
    GYEONGBUK("경북", "37"),
    GYEONGNAM("경남", "38"),
    JEJU("제주", "39");

    private final String displayName;
    private final String code;

    RegionCode(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String displayName() {
        return displayName;
    }

    public String code() {
        return code;
    }

    // 화면에서 선택한 지역명 → API 지역코드. 못 찾으면 null
    public static String codeOf(String displayName) {
        if (displayName == null) return null;
        for (RegionCode r : values()) {
            if (r.displayName.equals(displayName)) return r.code;
        }
        return null;
    }
}
