package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "weekly_rankings")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyRankingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "weekly_book_count")
    private int weeklyBookCount;

    @Column(name = "consecutive_days")
    private int consecutiveDays;

    @Column(name = "rank_position")
    private int rankPosition;
}