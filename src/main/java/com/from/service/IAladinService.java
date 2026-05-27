package com.from.service;

import com.from.dto.BookSearchDto;

import java.util.List;



public interface IAladinService {


    //알라딘 API로 책 검색 최대 10건
    List<BookSearchDto> searchBooks(String query, String type);

   //베스트셀러 10개 추출
    List<BookSearchDto> getBestseller();

    //책 표지 조회
    String searchCover(String title);
}