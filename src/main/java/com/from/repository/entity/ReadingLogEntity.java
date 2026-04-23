package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * reading_logs 테이블과 매핑되는 JPA 엔티티.
 * 사용자가 책을 등록할 때마다 해당 날짜의 독서 기록을 남긴다.
 *
 * 주요 용도:
 * - 주간 랭킹: 이번 주 유저별 책 수를 집계하여 순위를 계산한다.
 * - 연속 독서일: 오늘부터 연속으로 독서한 날짜 수를 계산한다.
 * - 대시보드: 독서 날짜 기반의 캘린더 및 차트 데이터에 활용된다.
 */
@Entity
@Table(name = "reading_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingLogEntity {

    /** 로그 고유 ID (AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 독서한 사용자의 로그인 ID */
    @Column(name = "user_id")
    private String userId;

    /** 읽은 책의 ID */
    @Column(name = "book_id")
    private Long bookId;

    /** 독서(책 등록) 날짜. LocalDate.now()로 설정된다. */
    @Column(name = "read_date")
    private LocalDate readDate;
}
