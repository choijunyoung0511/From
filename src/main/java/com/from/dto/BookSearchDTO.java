package com.from.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 책 데이터를 담는 통합 DTO.
 * DB에서 조회한 책(bookId, createdAt 포함)과
 * 알라딘 API 검색 결과(isbn 포함) 모두에 사용된다.
 * 사용하지 않는 필드는 null로 직렬화에서 제외된다.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookSearchDTO(
        Long bookId,
        String title,
        String author,
        String cover,
        String isbn,
        String description,
        String category,
        LocalDateTime createdAt
) {}