package com.from.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record BookSearchDto(
        Long bookId,
        String title,
        String author,
        String cover,
        String isbn,
        String description,
        String category,
        LocalDateTime createdAt
) {}