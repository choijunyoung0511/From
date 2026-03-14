package com.from.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void resetAndRecalculateRankings() {
        log.info("랭킹 초기화 시작");

        // 1. 기존 데이터 삭제
        jdbcTemplate.update("DELETE FROM weekly_rankings");

        // 2. 이번 주 월요일
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);

        // 3. 주간 독서량 집계
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT user_id, COUNT(DISTINCT book_id) AS weekly_book_count " +
                        "FROM reading_logs " +
                        "WHERE read_date >= ? " +
                        "GROUP BY user_id " +
                        "ORDER BY weekly_book_count DESC",
                weekStart
        );

        // 4. 연속 독서일 계산 후 INSERT
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = results.get(i);
            Long userId = (Long) row.get("user_id");
            int bookCount = ((Number) row.get("weekly_book_count")).intValue();
            int consecutiveDays = getConsecutiveDays(userId);

            jdbcTemplate.update(
                    "INSERT INTO weekly_rankings (user_id, week_start_date, weekly_book_count, consecutive_days, rank_position) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    userId, weekStart, bookCount, consecutiveDays, i + 1
            );
        }

        log.info("랭킹 초기화 완료 - 총 {}명", results.size());
    }

    private int getConsecutiveDays(Long userId) {
        List<LocalDate> dates = jdbcTemplate.queryForList(
                "SELECT DISTINCT read_date FROM reading_logs " +
                        "WHERE user_id = ? ORDER BY read_date DESC",
                LocalDate.class, userId
        );

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