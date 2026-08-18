package com.from.dto;

public record ImageDetailDto(
        String imageUrl,
        String inputImageUrl,
        String style,
        Long bookId,
        String bookTitle
) {}