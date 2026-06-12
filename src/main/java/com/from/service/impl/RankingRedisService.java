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


//레디스 랭킹 sorted set을 이용한 주간 랭킹 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingRedisService implements com.from.service.IRankingService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_USERNAME =
            //사용자 아이디 가져오기
            "SELECT username FROM users WHERE user_id = ?";

    private static final String SELECT_PROFILE =
            //프로필 사진가져오기
            "SELECT profile_image_url FROM users WHERE user_id = ?";

    private static final String SELECT_READ_DATES =
            //독서 날짜 조회
            "SELECT DISTINCT read_date FROM reading_logs " +
            "WHERE user_id = ? ORDER BY read_date DESC";
//현재 시간 기준으로 키를 만듬 (월요일 날짜 기준 !!)
    public String weeklyKey() {
        // 현재 날짜 기준 이번 주 월요일 계산
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        //레디스 키반환
        return "ranking:weekly:" + monday;
    }



    public void incrementBookCount(String userId) {
        String key = weeklyKey();
        redisTemplate.opsForZSet().incrementScore(key, userId, 1);
        //userId의 점수 +1 증가
        redisTemplate.expire(key, 14, TimeUnit.DAYS);
        //레디스 키를 14일뒤 자동삭제
        // 이번주 데이터가 다음주까지는 남아있어도 되고 너무 오래되면
        // redis 메모리를 차지하기 떄문에 자동삭제하려고 14일로 설정
        log.info("[RankingRedisService] ZINCRBY key={} member={}", key, userId);
    }


    //테스트용 초기 데이터 세팅용임 무시해도됨
    public void setScore(String key, String userId, int score) {
        redisTemplate.opsForZSet().add(key, userId, score);
    }
    public void deleteKey(String key) {
        redisTemplate.delete(key);
        log.info("[RankingRedisService] DELETE key={}", key);
    }
    // 여기까지 임시 테스트 코드


    //현재 주 redis key 조회
    public List<RankingDto> getWeeklyRankings() {
        String key = weeklyKey();

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
        // 점수높은 순서로 전체 조회하는 코드임


        //랭킹 데이터 없음
        if (tuples == null || tuples.isEmpty()) {
            log.info("[RankingRedisService] 랭킹 데이터 없음 key={}", key);
            return new ArrayList<>();
        }

        List<RankingDto> result = new ArrayList<>();
        //순위 시작값
        int rank = 1;


        //레디스 랭킹 데이터 반복
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            //userId
            String userId      = tuple.getValue();
            //score 값
            int    weeklyCount = tuple.getScore() != null ? tuple.getScore().intValue() : 0;

            //DB에서 사용자 이름 조회
            String username    = findUsername(userId);
            //연속일 계산
            int    consecutive = getConsecutiveDays(userId);
            //프로필 이미지 조회
            String profileUrl  = findProfileImageUrl(userId);


            // DTO 생성 후 리스트 저장
            result.add(new RankingDto(rank++, userId, username, weeklyCount, consecutive, profileUrl));
        }

        return result;
    }

    //사용자 조회

    private String findUsername(String userId) {
        try {
            return jdbcTemplate.queryForObject(SELECT_USERNAME, String.class, userId);
        } catch (Exception e) {
            //예외처리
            log.warn("[RankingRedisService] username 조회 실패 userId={}", userId);
            return userId;
        }
    }
    //프로필 조회
    private String findProfileImageUrl(String userId) {
        try {
            //이미지 프로필 조회
            return jdbcTemplate.queryForObject(SELECT_PROFILE, String.class, userId);
        } catch (Exception e) {
            return null;
        }
    }



    /** * 연속 독서일 계산
     * * * 로직: * 오늘 → 어제 → 그제 순으로 * 날짜가 이어지는지 검사 * * 끊기는 순간 break */
    private int getConsecutiveDays(String userId) {
        // queryForList(sql, LocalDate.class) 는 JDBC Date → LocalDate 자동변환 불가
        // query() + 람다로 직접 변환해야 함
        List<LocalDate> dates = jdbcTemplate.query(
                SELECT_READ_DATES,
                (rs, rowNum) -> rs.getDate("read_date").toLocalDate(),
                userId
        );
        //연속n일 계산 로직
        int streak = 0;
        LocalDate expected = LocalDate.now();
        //reading_log 테이블에서 독서날짜를 최신순으로 조회한 뒤, 오늘 날짜부터 하루씩 감소시키며 연속여부를 검사함.
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