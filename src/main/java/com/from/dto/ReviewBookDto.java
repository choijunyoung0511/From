package com.from.dto;

public record ReviewBookDto(
        // 독후감 작성화면에 표시하기위해 만든 DTO이며 필요한 타입만 표시
        String title,
        String author,
        String emoji) {}