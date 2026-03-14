package com.from.dto.ranking;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RankingDto {
    private long rank;
    private String username;
    private int weeklyCount;
    private int consecutiveDays;
}