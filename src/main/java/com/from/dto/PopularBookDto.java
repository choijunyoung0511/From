package com.from.dto;

import lombok.Builder;

//인기대출도서 조회결과 DTO (data4library loanItemSrch)
@Builder
public record PopularBookDto(
        int ranking,
        String bookName,
        String authors,
        String publisher,
        String isbn13,
        String bookImageUrl,
        int loanCount
) {}
