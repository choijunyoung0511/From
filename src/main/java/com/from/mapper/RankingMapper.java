package com.from.mapper;

import com.from.dto.RankingDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 주간 랭킹 조회 MyBatis 매퍼 인터페이스.
 * SQL 은 resources/mapper/RankingMapper.xml 에 정의되어 있다.
 */
@Mapper
public interface RankingMapper {

    /**
     * 현재 주간 랭킹을 순위 오름차순으로 조회한다.
     * weekly_rankings 테이블은 RankingScheduler 가 매일 새벽 3시에 갱신한다.
     *
     * @return 랭킹 목록 (rankPosition 오름차순)
     */
    List<RankingDto> getWeeklyRanking();
}
