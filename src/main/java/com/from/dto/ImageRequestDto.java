package com.from.dto;

/**
 * 이미지 생성 요청 파라미터를 담는 DTO.
 * ImageController → ImageService 로 전달되며,
 * Gemini API 호출에 필요한 정보를 하나의 객체로 묶는다.
 *
 * bookId:    선택한 책의 DB ID (imge_results 테이블에 저장)
 * bookTitle: 책 제목 (Gemini 프롬프트에 포함)
 * scene:     사용자가 입력한 책 속 장면 설명
 * style:     선택한 아트 스타일 (watercolor, cartoon, realistic 등)
 */
public record ImageRequestDto(Long bookId, String bookTitle, String scene, String style) {}