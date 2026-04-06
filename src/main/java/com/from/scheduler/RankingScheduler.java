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

/**
 * 주간 랭킹을 매일 새벽 3시에 초기화·재계산하는 스케줄러.
 * reading_logs 테이블의 이번 주 독서 기록을 집계하여
 * weekly_rankings 테이블을 갱신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 매일 새벽 3시에 실행.
     * 기존 weekly_rankings 를 삭제하고, reading_logs 를 기반으로 새 랭킹을 삽입한다.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void resetAndRecalculateRankings() {
        log.info("{}.resetAndRecalculateRankings Start!", this.getClass().getName());

        // 기존 랭킹 데이터 전체 삭제
        jdbcTemplate.update("DELETE FROM weekly_rankings");

        // 이번 주 월요일 기준일 계산
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);

        // 이번 주 월요일 이후의 독서 로그를 유저별로 집계 (책 수 내림차순)
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT user_id, COUNT(DISTINCT book_id) AS weekly_book_count " +
                        "FROM reading_logs " +
                        "WHERE read_date >= ? " +
                        "GROUP BY user_id " +
                        "ORDER BY weekly_book_count DESC",
                weekStart
        );

        // 각 유저의 연속 독서일을 계산한 뒤 weekly_rankings 에 삽입
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = results.get(i);

            // user_id 는 VARCHAR — String 으로 캐스팅한다
            String userId   = (String) row.get("user_id");
            int bookCount   = ((Number) row.get("weekly_book_count")).intValue();
            int consecutive = getConsecutiveDays(userId);

            jdbcTemplate.update(
                    "INSERT INTO weekly_rankings " +
                            "(user_id, week_start_date, weekly_book_count, consecutive_days, rank_position) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    userId, weekStart, bookCount, consecutive, i + 1
            );
        }

        log.info("{}.resetAndRecalculateRankings End! - 총 {}명 처리",
                this.getClass().getName(), results.size());
    }

    /**
     * 특정 유저의 오늘 기준 연속 독서일을 계산한다.
     * reading_logs 에서 날짜를 내림차순으로 가져와 하루도 빠지지 않은 연속 일수를 센다.
     *
     * @param userId 로그인 ID
     * @return 연속 독서일 수 (오늘이 없으면 0)
     */
    private int getConsecutiveDays(String userId) {
        // 해당 유저의 고유 독서 날짜를 최신순으로 조회
        List<LocalDate> dates = jdbcTemplate.queryForList(
                "SELECT DISTINCT read_date FROM reading_logs " +
                        "WHERE user_id = ? ORDER BY read_date DESC",
                LocalDate.class, userId
        );

        int streak = 0;
        LocalDate expected = LocalDate.now();

        // 오늘부터 하루씩 거슬러 올라가며 연속 여부 확인
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
