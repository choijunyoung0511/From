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

    private static final String SELECT_PROFILE =
            "SELECT profile_image_url FROM users WHERE user_id = ?";

    private static final String SELECT_READ_DATES =
            "SELECT DISTINCT read_date FROM reading_logs " +
            "WHERE user_id = ? ORDER BY read_date DESC";
//현재 시간 기준으로 키를 만듬 (월요일 날짜로 !!)
    public String weeklyKey() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        return "ranking:weekly:" + monday;
    }

    public void incrementBookCount(String userId) {
        String key = weeklyKey();
        redisTemplate.opsForZSet().incrementScore(key, userId, 1);
        //userId의 점수 +1 증가
        redisTemplate.expire(key, 14, TimeUnit.DAYS);
        //레디스 키를 14일뒤 자동삭제
        // 이번주 데이터가 다음주까지는 남아있어도 되고 너무 오래되면 redis 메모리를 차지하기 떄문에 자동삭제하려고 14일로 설정
        log.info("[RankingRedisService] ZINCRBY key={} member={}", key, userId);
    }


    public void setScore(String key, String userId, int score) {
        redisTemplate.opsForZSet().add(key, userId, score);
    }

    public void deleteKey(String key) {
        redisTemplate.delete(key);
        log.info("[RankingRedisService] DELETE key={}", key);
        // 래디스 키 제거 로직
    }

    public List<RankingDto> getWeeklyRankings() {
        String key = weeklyKey();

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
        // 점수높은 순서로 전체 조회하는 코드임

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
            String profileUrl  = findProfileImageUrl(userId);

            result.add(new RankingDto(rank++, userId, username, weeklyCount, consecutive, profileUrl));
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

    private String findProfileImageUrl(String userId) {
        try {
            return jdbcTemplate.queryForObject(SELECT_PROFILE, String.class, userId);
        } catch (Exception e) {
            return null;
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
        //reading_log 테이블에서 독서날짜를 최신순으로 조회한 뒤, 오늘 날짜부터 하루씩 감소시키며 연속여부를 검사했습니다.
        // 날짜가 끊기는 순간 break 로 반복문을 종료해서 연속 독서일을 계산함
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