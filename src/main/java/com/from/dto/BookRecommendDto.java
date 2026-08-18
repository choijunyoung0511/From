package com.from.dto;

public record BookRecommendDto(
        String title,
        String author,
        String cover,
        String reason) {}