package com.from.dto;

import lombok.Builder;

import java.util.List;

@Builder
// 단순리스트가 아닌 추천 섹션단위로 관리,추천 유형,화면 표시명,이이콘, 추천독서 목록을 함꼐 전달하도록 설계
public record RecommendSectionDto(
        //
        String type,
        String label,
        String icon,
        List<BookSearchDto> books //추천책이 여러권 이라서 리스트 반환
) {}