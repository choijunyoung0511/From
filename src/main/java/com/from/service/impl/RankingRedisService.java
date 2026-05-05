package com.from.service.impl;

import com.from.dto.RankingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis Sorted Set을 이용한 주간 랭킹 핵심 서비스.
 *
 * Redis 키: ranking:weekly:{yyyy-MM-dd} (이번 주 월요일 날짜)
 * member  : userId
 * score   : 이번 주 읽은 책 수
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingRedisService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_USERNAME =
            "SELECT username FROM users WHERE user_id = ?";

    private static final String SELECT_READ_DATES =
            "SELECT DISTINCT read_date FROM reading_logs " +
            "WHERE user_id = ? ORDER BY read_date DESC";

    /**
     * 이번 주 월요일 날짜 기준으로 Redis 키를 생성한다.
     * 예: "ranking:weekly:2025-05-05"
     */
    public String weeklyKey() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        return "ranking:weekly:" + monday;
    }

    /**
     * 책 등록 시 호출 — 해당 유저의 이번 주 책 카운트를 1 증가시킨다.
     * TTL은 14일로 설정한다.
     */
    public void incrementBookCount(String userId) {
        String key = weeklyKey();
        redisTemplate.opsForZSet().incrementScore(key, userId, 1);
        redisTemplate.expire(key, 14, TimeUnit.DAYS);
        log.info("[RankingRedisService] ZINCRBY key={} member={}", key, userId);
    }

    /**
     * 스케줄러 보정 시 사용 — 특정 키에 유저별 점수를 직접 설정한다.
     */
    public void setScore(String key, String userId, int score) {
        redisTemplate.opsForZSet().add(key, userId, score);
    }

    /**
     * 특정 Redis 키를 삭제한다.
     */
    public void deleteKey(String key) {
        redisTemplate.delete(key);
        log.info("[RankingRedisService] DELETE key={}", key);
    }

    /**
     * 이번 주 랭킹을 점수 내림차순으로 조회한다.
     * 유저 닉네임과 연속 독서일은 DB에서 추가 조회한다.
     */
    public List<RankingDto> getWeeklyRankings() {
        String key = weeklyKey();

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            log.info("[RankingRedisService] 랭킹 데이터 없음 key={}", key);
            return new ArrayList<>();
        }

        List<RankingDto> result = new ArrayList<>();
        int rank = 1;

        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String userId      = tuple.getValue();
            int    weeklyCount = tuple.getScore() != null ? tuple.getScore().intValue() : 0;
            String username    = findUsername(userId);
            int    consecutive = getConsecutiveDays(userId);

            result.add(new RankingDto(rank++, userId, username, weeklyCount, consecutive));
        }

        return result;
    }

    private String findUsername(String userId) {
        try {
            return jdbcTemplate.queryForObject(SELECT_USERNAME, String.class, userId);
        } catch (Exception e) {
            log.warn("[RankingRedisService] username 조회 실패 userId={}", userId);
            return userId;
        }
    }

    private int getConsecutiveDays(String userId) {
        // queryForList(sql, LocalDate.class) 는 JDBC Date → LocalDate 자동변환 불가
        // query() + 람다로 직접 변환해야 함
        List<LocalDate> dates = jdbcTemplate.query(
                SELECT_READ_DATES,
                (rs, rowNum) -> rs.getDate("read_date").toLocalDate(),
                userId
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