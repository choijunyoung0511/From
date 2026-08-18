package com.from.dto;

public record BookRatingDto(
        Long id,
        String userId,
        String displayName,
        String profileImageUrl,
        int rating,
        String content,
        String createdAt,
        long likeCount,
        long commentCount,
        boolean liked,
        boolean isOwn
) {}