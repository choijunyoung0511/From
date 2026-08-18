package com.from.service;

import com.from.dto.RankingDto;

import java.util.List;

public interface IRankingService {

    List<RankingDto> getWeeklyRankings();

    void incrementBookCount(String userId);
}