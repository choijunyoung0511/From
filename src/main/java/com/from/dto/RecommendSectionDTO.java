package com.from.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendSectionDTO(
        String type,
        String label,
        String icon,
        List<BookSearchDTO> books
) {}