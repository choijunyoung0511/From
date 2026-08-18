package com.from.dto;

public record GeminiImageRequestDto(
        String prompt,
        String imageBase64,
        String imageMimeType
) {}