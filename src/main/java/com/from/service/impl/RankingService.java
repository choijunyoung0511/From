package com.from.service.impl;

import com.from.dto.RankingDto;
import com.from.repository.WeeklyRankingRepository;
import com.from.service.IRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주간 랭킹 서비스 구현체.
 * weekly_rankings 테이블에서 현재 랭킹 데이터를 조회하여 반환한다.
 * 데이터 갱신은 RankingScheduler가 매일 새벽 3시에 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService implements IRankingService {

    private final WeeklyRankingRepository weeklyRankingRepository;

    /**
     * 이번 주 랭킹 전체를 순위 오름차순으로 조회한다.
     * 유저 닉네임(username)을 포함하기 위해 users 테이블과 조인한 JPQL을 사용한다.
     */
    @Override
    public List<RankingDto> getWeeklyRankings() {
        log.info("{}.getWeeklyRankings Start!", this.getClass().getName());
        List<RankingDto> result = weeklyRankingRepository.findAllAsRankingDto();
        log.info("{}.getWeeklyRankings End! - {}명", this.getClass().getName(), result.size());
        return result;
    }
}