package com.from.service.impl;

import com.from.dto.RankingDto;
import com.from.repository.WeeklyRankingRepository;
import com.from.service.IRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService implements IRankingService {

    private final WeeklyRankingRepository weeklyRankingRepository;

    @Override
    public List<RankingDto> getWeeklyRankings() {
        log.info("{}.getWeeklyRankings Start!", this.getClass().getName());
        List<RankingDto> result = weeklyRankingRepository.findAllAsRankingDto();
        log.info("{}.getWeeklyRankings End! - {}명", this.getClass().getName(), result.size());
        return result;
    }
}