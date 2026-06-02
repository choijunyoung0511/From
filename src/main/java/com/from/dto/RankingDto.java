package com.from.dto;

//주간 독서 랭킹화면 데이터 전달용 DTO
public record RankingDto(
        int rankPosition,
        String userId,
        String userName,
        int weeklyBookCount,
        int consecutiveDays,
        String profileImageUrl
) {}