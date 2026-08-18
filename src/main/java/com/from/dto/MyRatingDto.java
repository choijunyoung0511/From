package com.from.dto;

import lombok.Builder;

@Builder
public record MyRatingDto(
        //사용자가 작성한 독서 후기 조회용 DTO
        Long id,
        Long bookId,
        String bookTitle,
        String bookAuthor,
        int rating,
        String content,
        String createdAt
) {}