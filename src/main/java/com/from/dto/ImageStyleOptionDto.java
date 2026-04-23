package com.from.dto;

/**
 * 스타일 선택 화면(step2-style.html)에 표시할 아트 스타일 옵션 하나를 담는 DTO.
 * ImageService.getStyleOptions()가 목록을 반환하고,
 * ImageController가 Model에 담아 템플릿에 전달한다.
 *
 * value:       서버에 전송되는 스타일 코드 (watercolor, cartoon, realistic 등)
 * label:       화면에 표시되는 한국어 이름 (수채화, 만화/애니메이션 등)
 * description: 스타일 설명 툴팁 (예: "부드럽고 몽환적인 수채화 스타일")
 */
public record ImageStyleOptionDto(String value, String label, String description) {}