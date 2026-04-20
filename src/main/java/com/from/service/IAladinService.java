package com.from.service;

import java.util.List;
import java.util.Map;

public interface IAladinService {

    // 책 제목/저자/키워드로 검색
    List<Map<String, String>> searchBooks(String query, String type);

    // 베스트셀러 Top 10 조회
    List<Map<String, String>> getBestseller();

    // 제목으로 표지 이미지 URL 1건 조회
    String searchCover(String title);
}