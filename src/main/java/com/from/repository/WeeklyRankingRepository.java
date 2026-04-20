package com.from.repository;

import com.from.dto.RankingDto;
import com.from.repository.entity.WeeklyRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WeeklyRankingRepository extends JpaRepository<WeeklyRankingEntity, Long> {

    @Query("SELECT new com.from.dto.RankingDto(wr.rankPosition, wr.userId, u.username, wr.weeklyBookCount, wr.consecutiveDays) " +
           "FROM WeeklyRankingEntity wr JOIN UserInfoEntity u ON wr.userId = u.userId " +
           "ORDER BY wr.rankPosition ASC")
    List<RankingDto> findAllAsRankingDto();

    @Modifying
    @Query("DELETE FROM WeeklyRankingEntity")
    void deleteAllInBatch();
}