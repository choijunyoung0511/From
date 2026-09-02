package com.from.dto;

import lombok.Builder;

// 도서관 검색결과 DTO (data4library libSrch)
// libCode는 내부적으로 bookExist 호출에만 사용하고 화면에는 노출하지 않는다
@Builder
public record LibraryDto(
        String libCode,
        String libName,
        String address,
        String tel,
        String homepage,
        String closed,
        String operatingTime
) {}
