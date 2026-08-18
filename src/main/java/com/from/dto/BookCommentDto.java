package com.from.dto;

public record BookCommentDto(
        String userId,
        String content,
        String createdAt) {}