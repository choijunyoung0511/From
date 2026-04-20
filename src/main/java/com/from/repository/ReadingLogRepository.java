package com.from.repository;

import com.from.repository.entity.ReadingLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReadingLogRepository extends JpaRepository<ReadingLogEntity, Long> {

    // 이번 주 이후 유저별 책 수 집계 (weeklyBookCount 내림차순)
    @Query("SELECT rl.userId, COUNT(DISTINCT rl.bookId) FROM ReadingLogEntity rl " +
           "WHERE rl.readDate >= :weekStart GROUP BY rl.userId ORDER BY COUNT(DISTINCT rl.bookId) DESC")
    List<Object[]> aggregateBookCountByUserSince(@Param("weekStart") LocalDate weekStart);

    // 특정 유저의 고유 독서 날짜 최신순 조회 (연속 독서일 계산용)
    @Query("SELECT DISTINCT rl.readDate FROM ReadingLogEntity rl " +
           "WHERE rl.userId = :userId ORDER BY rl.readDate DESC")
    List<LocalDate> findDistinctReadDatesByUserId(@Param("userId") String userId);
}