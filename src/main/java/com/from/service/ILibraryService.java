package com.from.service;

import com.from.dto.LibraryBookAvailabilityDto;
import com.from.dto.LibraryBookSearchDto;
import com.from.dto.LibraryDto;
import com.from.dto.PopularBookDto;

import java.util.List;

public interface ILibraryService {

    // 전국 기준 인기대출도서 TOP 10 조회. API 실패/미인증 시 빈 리스트 반환
    List<PopularBookDto> getPopularBooks();

    // 특정 도서관(libCode)의 특정 도서(isbn13) 소장/대출가능 여부 조회
    // API 미인증/파라미터 누락/호출 실패 시 null 반환 (Controller에서 안내 메시지 처리)
    LibraryBookAvailabilityDto checkBookAvailability(String libCode, String isbn13);

    // 도서관명으로 도서관 검색 (libCode를 사용자에게 직접 입력받지 않기 위한 검색 기능)
    // null = API 실패, 빈 리스트 = 정상 조회했지만 일치하는 도서관 없음
    List<LibraryDto> searchLibraries(String libName);

    // 책 제목으로 도서 검색 (FROM DB의 BookEntity와 무관 - 등록 안 된 책도 검색 가능)
    // null = API 실패, 빈 리스트 = 검색 결과 없음
    List<LibraryBookSearchDto> searchBooks(String title);

    // ISBN13 + 지역코드로 해당 책을 소장한 도서관 목록 조회
    // null = API 실패, 빈 리스트 = 해당 지역에 소장 도서관 없음
    List<LibraryDto> findLibrariesByBook(String isbn13, String regionCode);
}
