package com.from.controller;

import com.from.dto.RankingDto;
import com.from.service.impl.RankingRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 주간 랭킹 화면을 처리하는 컨트롤러.
 * Redis Sorted Set 기반 RankingRedisService를 직접 사용한다.
 * (IRankingService 래퍼 제거 — 불필요한 위임 계층 단순화)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RankingController {

    private final RankingRedisService rankingRedisService;

    @GetMapping("/ranking")
    public String ranking(Model model) {
        log.info("{}.ranking Start!", this.getClass().getName());

        List<RankingDto> rankings = rankingRedisService.getWeeklyRankings();

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM.dd");
        String weekLabel = monday.format(fmt) + " ~ " + sunday.format(fmt) + " 주간 랭킹";

        model.addAttribute("rankings", rankings);
        model.addAttribute("weekLabel", weekLabel);

        log.info("{}.ranking End! - {}명", this.getClass().getName(), rankings.size());
        return "ranking/ranking";
    }
}