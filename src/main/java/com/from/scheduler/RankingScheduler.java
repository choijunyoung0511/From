package com.from.scheduler;

import com.from.repository.ReadingLogRepository;
import com.from.repository.WeeklyRankingRepository;
import com.from.repository.entity.WeeklyRankingEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 주간 랭킹을 매일 새벽 3시에 초기화·재계산하는 스케줄러.
 * reading_logs 테이블의 이번 주 독서 기록을 집계하여
 * weekly_rankings 테이블을 갱신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final WeeklyRankingRepository weeklyRankingRepository;
    private final ReadingLogRepository readingLogRepository;

    /**
     * 매일 새벽 3시에 실행.
     * 기존 weekly_rankings 를 삭제하고, reading_logs 를 기반으로 새 랭킹을 삽입한다.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void resetAndRecalculateRankings() {
        log.info("{}.resetAndRecalculateRankings Start!", this.getClass().getName());

        weeklyRankingRepository.deleteAllInBatch();

        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);

        // 이번 주 유저별 독서 책 수 집계 (내림차순)
        List<Object[]> results = readingLogRepository.aggregateBookCountByUserSince(weekStart);

        for (int i = 0; i < results.size(); i++) {
            Object[] row = results.get(i);
            String userId   = (String) row[0];
            int bookCount   = ((Number) row[1]).intValue();
            int consecutive = getConsecutiveDays(userId);

            weeklyRankingRepository.save(WeeklyRankingEntity.builder()
                    .userId(userId)
                    .weekStartDate(weekStart)
                    .weeklyBookCount(bookCount)
                    .consecutiveDays(consecutive)
                    .rankPosition(i + 1)
                    .build());
        }

        log.info("{}.resetAndRecalculateRankings End! - 총 {}명 처리",
                this.getClass().getName(), results.size());
    }

    /**
     * 특정 유저의 오늘 기준 연속 독서일을 계산한다.
     */
    private int getConsecutiveDays(String userId) {
        List<LocalDate> dates = readingLogRepository.findDistinctReadDatesByUserId(userId);

        int streak = 0;
        LocalDate expected = LocalDate.now();

        for (LocalDate date : dates) {
            if (date.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }
}