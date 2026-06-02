package com.from.dto;

//이미지 생성 요청 파라미터 담는 DTO
public record ImageRequestDto(
        Long bookId,
        String bookTitle,
        String scene,
        String style) {}