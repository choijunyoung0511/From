package com.from.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * 이미지 생성 기능에서 사용하는 DTO 모음.
 * 요청/응답/스타일 옵션 세 가지 내부 레코드로 구성된다.
 */
public class ImageDto {

    /**
     * 이미지 생성 요청 파라미터.
     * Controller 에서 세션 데이터를 모아 Service 에 전달할 때 사용한다.
     *
     * @param bookId    책 ID (books.book_id)
     * @param bookTitle 책 제목 (프롬프트 생성에 사용)
     * @param scene     변환할 장면 설명
     * @param style     이미지 아트 스타일 (watercolor, cartoon 등)
     */
    public record GenerateRequest(Long bookId, String bookTitle, String scene, String style) {}

    /**
     * 이미지 생성 응답 DTO.
     * 성공 시 imageId·imageUrl, 실패 시 errorMessage 를 담아 반환한다.
     *
     * @param imageId      DB에 저장된 이미지 결과 ID
     * @param imageUrl     base64 Data URL 또는 외부 URL
     * @param style        적용된 아트 스타일
     * @param bookTitle    원본 책 제목
     * @param success      생성 성공 여부
     * @param errorMessage 실패 시 오류 메시지
     */
    @Builder
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public record GenerateResponse(
            Long imageId,
            String imageUrl,
            String style,
            String bookTitle,
            boolean success,
            String errorMessage
    ) {}

    /**
     * 이미지 스타일 옵션 (스타일 선택 화면에 표시).
     *
     * @param value       내부 값 (API 파라미터로 전달)
     * @param label       사용자에게 표시할 한국어 이름
     * @param description 스타일 설명
     */
    public record StyleOption(String value, String label, String description) {}
}