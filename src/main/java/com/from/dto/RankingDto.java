package com.from.dto;

/**
 * 주간 랭킹 조회 결과를 담는 DTO.
 * MyBatis resultType 으로 직접 매핑된다.
 * {@code mybatis.configuration.map-underscore-to-camel-case=true} 설정으로
 * snake_case 컬럼이 camelCase 필드에 자동 매핑된다.
 */
public record RankingDto(
        int rankPosition,
        String userId,
        String userName,
        int weeklyBookCount,
        int consecutiveDays
) {}