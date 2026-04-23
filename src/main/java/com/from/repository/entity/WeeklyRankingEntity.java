package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * weekly_rankings 테이블과 매핑되는 JPA 엔티티.
 * RankingScheduler가 매일 새벽 3시에 이 테이블을 초기화하고 재계산하여 채운다.
 *
 * 랭킹 계산 기준:
 * - weeklyBookCount: 이번 주(월~일) 등록한 책 수 (주 순위 기준)
 * - consecutiveDays: 오늘 기준 연속 독서일 (동점 시 참고 지표)
 * - rankPosition: 순위 (1위부터 시작)
 */
@Entity
@Table(name = "weekly_rankings")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyRankingEntity {

    /** 랭킹 레코드 고유 ID (AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 순위에 오른 사용자의 로그인 ID */
    @Column(name = "user_id")
    private String userId;

    /** 이번 주 시작일 (월요일 날짜) */
    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    /** 이번 주에 등록한 책 수 */
    @Column(name = "weekly_book_count")
    private int weeklyBookCount;

    /** 오늘 기준 연속 독서일 */
    @Column(name = "consecutive_days")
    private int consecutiveDays;

    /** 순위 (1부터 시작) */
    @Column(name = "rank_position")
    private int rankPosition;
}