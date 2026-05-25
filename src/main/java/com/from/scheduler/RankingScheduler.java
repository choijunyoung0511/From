package com.from.scheduler;

import com.from.service.impl.RankingRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


// 레디스 보정 스케줄러 : 서버 재시작 + redis 장애 후 데이터 정합성 복구, 새 주 시작시  지난 주 키를 정리하고 이번 주 키를 초기화

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    // 레디스 키 만료 설정에 사용
    private final StringRedisTemplate redisTemplate;

    // 집계쿼리에 사용
    private final JdbcTemplate jdbcTemplate;

    // 레디스 sorted set 쓰기
    private final RankingRedisService rankingRedisService;

    private static final String AGGREGATE_SQL =
            "SELECT user_id, COUNT(DISTINCT book_id) AS weekly_book_count " +
                    "FROM reading_logs " +
                    "WHERE read_date >= ? " +
                    "GROUP BY user_id " +
                    "ORDER BY weekly_book_count DESC";


    // 매주 월요일 실행, 이번 주 redis 키를 초기화 재동기화 한다
    // 이번주 월요일 날짜 계산 > 기존 레디스 키 삭제 > reading_logs에서 유저별 책 수 집계 > redis sorted set에 zadd로 재삽입 > ttl 14일 설정
    @Scheduled(cron = "0 1 0 * * MON")
    public void syncWeeklyRankingToRedis() {
        log.info("{}.syncWeeklyRankingToRedis Start!", this.getClass().getName());

        // ── STEP 1. 이번 주 월요일 날짜 및 Redis 키 계산 ─────────────────────
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);

        // ---STEP 1. RankingRedisService의 weeklyKey() 호출
        String key = rankingRedisService.weeklyKey();

        // ── STEP 2. 기존 Redis 키 삭제 (이전 주 데이터 초기화) ───────────────
        rankingRedisService.deleteKey(key);

        // ── STEP 3. reading_logs에서 이번 주 유저별 독서량 집계 ───────────────
        List<Map<String, Object>> results = jdbcTemplate.queryForList(AGGREGATE_SQL, weekStart);

        if (results.isEmpty()) {
            log.info("{}.syncWeeklyRankingToRedis - 이번 주 독서 기록 없음", this.getClass().getName());
            return;
        }

        // ── STEP 4. Redis Sorted Set에 유저별 점수 재삽입 ────────────────────
        for (Map<String, Object> row : results) {
            String userId   = String.valueOf(row.get("user_id"));
            int    bookCount = ((Number) row.get("weekly_book_count")).intValue();

            // ZADD key score member
            rankingRedisService.setScore(key, userId, bookCount);
        }

        // ── STEP 5. TTL 14일 설정 (다음 주 데이터로 안전하게 전환) ──────────
        redisTemplate.expire(key, 14, TimeUnit.DAYS);

        log.info("{}.syncWeeklyRankingToRedis End! - 총 {}명 동기화",
                this.getClass().getName(), results.size());
    }
}