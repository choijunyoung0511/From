package com.from.service;

import com.from.dto.BookSearchDTO;

import java.util.List;

/**
 * 알라딘 오픈 API 호출 서비스 인터페이스.
 * BookController가 이 인터페이스를 통해 책 검색과 베스트셀러 조회를 요청한다.
 * 구현체는 AladinService(service/impl/)에 있다.
 */
public interface IAladinService {

    /**
     * 알라딘 API로 책을 검색한다.
     *
     * @param query 검색어 (책 제목, 저자명, 키워드 등)
     * @param type  검색 유형 (Title / Author / Keyword)
     * @return 검색 결과 목록 (최대 10건)
     */
    List<BookSearchDTO> searchBooks(String query, String type);

    /**
     * 알라딘 베스트셀러 Top 10을 조회한다.
     * 책 등록 화면의 "베스트셀러" 탭에 표시된다.
     *
     * @return 베스트셀러 목록 (최대 10건)
     */
    List<BookSearchDTO> getBestseller();

    /**
     * 책 제목으로 알라딘에서 표지 이미지 URL을 1건 조회한다.
     * AI 추천 결과에 표지 이미지를 표시할 때 사용한다.
     *
     * @param title 책 제목
     * @return 표지 이미지 URL (없으면 빈 문자열)
     */
    String searchCover(String title);
}