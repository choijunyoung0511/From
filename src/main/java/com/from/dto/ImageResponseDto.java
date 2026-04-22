package com.from.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record ImageResponseDto(
        Long imageId,
        String imageUrl,
        String style,
        String bookTitle,
        boolean success,
        String errorMessage
) {}