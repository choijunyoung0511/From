package com.from.service;

import com.from.dto.RankingDto;

import java.util.List;

/**
 * 주간 랭킹 서비스 인터페이스.
 * RankingController가 이 인터페이스를 통해 서비스를 호출한다.
 * 구현체는 RankingService(service/impl/)에 있다.
 */
public interface IRankingService {

    /**
     * 이번 주 랭킹 전체를 순위 오름차순으로 조회한다.
     * weekly_rankings 테이블은 RankingScheduler가 매일 새벽 3시에 갱신한다.
     *
     * @return 순위 오름차순 RankingDto 목록
     */
    List<RankingDto> getWeeklyRankings();
}