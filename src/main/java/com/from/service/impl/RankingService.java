package com.from.service.impl;

import com.from.dto.RankingDto;
import com.from.service.IRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주간 랭킹 서비스 구현체.
 * Redis Sorted Set에서 랭킹 데이터를 조회하여 반환한다.
 *
 * 데이터 흐름:
 *   쓰기: 책 등록 시 BookController → RankingRedisService.incrementBookCount()
 *   읽기: RankingController → RankingService → RankingRedisService.getWeeklyRankings()
 *   보정: 매주 월요일 RankingScheduler → Redis 재동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService implements IRankingService {

    /** Redis Sorted Set 랭킹 핵심 서비스 */
    private final RankingRedisService rankingRedisService;

    /**
     * 이번 주 랭킹 전체를 점수(책 수) 내림차순으로 조회한다.
     * Redis ZREVRANGE 명령어로 O(log N) 읽기를 수행한다.
     *
     * @return 순위 내림차순 RankingDto 목록
     */
    @Override
    public List<RankingDto> getWeeklyRankings() {
        log.info("{}.getWeeklyRankings Start!", this.getClass().getName());
        List<RankingDto> result = rankingRedisService.getWeeklyRankings();
        log.info("{}.getWeeklyRankings End! - {}명", this.getClass().getName(), result.size());
        return result;
    }
}