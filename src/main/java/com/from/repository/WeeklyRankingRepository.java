package com.from.repository;

import com.from.dto.RankingDto;
import com.from.repository.entity.WeeklyRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * weekly_rankings 테이블에 접근하는 JPA Repository.
 * RankingScheduler가 매일 새벽 3시에 이 Repository를 통해 랭킹을 초기화·재삽입한다.
 */
public interface WeeklyRankingRepository extends JpaRepository<WeeklyRankingEntity, Long> {

    /**
     * 랭킹 순위 오름차순으로 정렬하여 유저 이름(username)을 조인한 결과를 RankingDto로 반환한다.
     * JPQL의 'new' 생성자 표현식으로 쿼리 결과를 바로 DTO로 변환한다.
     * users 테이블(UserInfoEntity)과 조인하여 userId 대신 username을 가져온다.
     *
     * @return 순위 오름차순 RankingDto 목록
     */
    @Query("SELECT new com.from.dto.RankingDto(wr.rankPosition, wr.userId, u.username, wr.weeklyBookCount, wr.consecutiveDays) " +
           "FROM WeeklyRankingEntity wr JOIN UserInfoEntity u ON wr.userId = u.userId " +
           "ORDER BY wr.rankPosition ASC")
    List<RankingDto> findAllAsRankingDto();

    /**
     * 모든 랭킹 레코드를 일괄 삭제한다.
     * RankingScheduler가 새 랭킹 삽입 전에 기존 데이터를 초기화할 때 사용한다.
     *
     * @Modifying: SELECT가 아닌 UPDATE/DELETE/INSERT 쿼리임을 Spring Data JPA에 알린다.
     */
    @Modifying
    @Query("DELETE FROM WeeklyRankingEntity")
    void deleteAllInBatch();
}