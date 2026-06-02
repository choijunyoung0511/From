package com.from.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

//이미지 결과 담는 DTO
@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record ImageResponseDto(
        Long imageId,       // DB에 저장된 이미지 결과 ID (step3 결과 화면 URL에 사용)
        String imageUrl,    // 이미지 생성 API가 반환한 URL 또는 base64 Data URL
        String style,       // 적용된 아트 스타일
        String bookTitle,   // 책 제목 (결과 화면 표시용)
        boolean success,    // 생성 성공 여부
        String errorMessage // 실패 시 오류 메시지
) {}