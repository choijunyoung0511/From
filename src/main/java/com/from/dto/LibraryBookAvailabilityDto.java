package com.from.dto;

import lombok.Builder;

// 도서관 소장/대출가능 여부 조회결과 DTO (data4library bookExist)
// hasBook/loanAvailable: 원본 API는 "Y"/"N" 문자열로 응답 → Service에서 boolean으로 변환해 전달
@Builder
public record LibraryBookAvailabilityDto(
        String libraryCode,
        String isbn13,
        boolean hasBook,
        boolean loanAvailable
) {}
