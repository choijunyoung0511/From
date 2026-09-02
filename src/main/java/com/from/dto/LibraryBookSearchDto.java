package com.from.dto;

import lombok.Builder;

// 도서관 정보나루 도서 검색결과 DTO (data4library srchBooks)
// FROM DB의 BookEntity와 무관한 독립 조회 결과 - FROM에 등록되지 않은 책도 검색 가능
@Builder
public record LibraryBookSearchDto(
        String bookName,
        String authors,
        String publisher,
        String publicationYear,
        String isbn13,
        String bookImageUrl
) {}
