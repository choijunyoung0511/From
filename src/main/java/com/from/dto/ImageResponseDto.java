package com.from.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * 이미지 생성 결과를 담는 DTO.
 * ImageService → ImageController → 브라우저(step2-style.html)로 JSON 응답된다.
 *
 * 성공 시: success=true, imageId·imageUrl 포함
 * 실패 시: success=false, errorMessage 포함
 *
 * @JsonInclude(NON_DEFAULT): false(boolean 기본값)인 success 필드가 생략되지 않도록
 *  NON_DEFAULT를 사용하지만, 실제로 false일 때도 포함시키기 위해 명시적으로 빌더에서 설정한다.
 */
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