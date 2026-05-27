package com.from.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record RecommendSectionDto(
        String type,
        String label,
        String icon,
        List<BookSearchDto> books
) {}