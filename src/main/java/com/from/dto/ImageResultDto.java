package com.from.dto;

import java.time.LocalDateTime;

//이미지 생성 완료 결과를 담는 DTO
public record ImageResultDto(
        Long imageId, //ID
        String imageUrl, //s3 경로
        String generatedPrompt, // 클로드가 생성한 이미지 프롬프트
        String bookTitle, //책 제목
        String style, //적용된 아트 스타일
        String sceneDescription, //사용자가 입력한 설명
        LocalDateTime createdAt // 생성일시
) {}