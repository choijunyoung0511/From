package com.from.service;

import com.from.dto.RankingDto;

import java.util.List;

public interface IRankingService {

    // 주간 랭킹 전체 조회 (순위 오름차순)
    List<RankingDto> getWeeklyRankings();
}