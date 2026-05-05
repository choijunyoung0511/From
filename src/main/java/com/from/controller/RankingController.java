package com.from.controller;

import com.from.dto.RankingDto;
import com.from.service.IRankingService;
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
 * /ranking 경로는 LoginInterceptor가 인증을 검사한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RankingController {

    /** 랭킹 조회 서비스 (Redis 기반) */
    private final IRankingService rankingService;

    /**
     * 주간 랭킹 화면을 반환한다.
     * 이번 주 월~일 날짜 범위 레이블과 랭킹 목록을 Model에 담아 전달한다.
     *
     * @param model rankings(랭킹 목록), weekLabel(날짜 표시용) 전달
     * @return ranking/ranking 템플릿
     */
    @GetMapping("/ranking")
    public String ranking(Model model) {
        log.info("{}.ranking Start!", this.getClass().getName());

        // ✅ 수정1: 주석 수정 — weekly_rankings 테이블 → Redis Sorted Set 조회
        List<RankingDto> rankings = rankingService.getWeeklyRankings();

        // 이번 주 월요일~일요일 날짜 계산
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        // 화면 상단에 표시할 주간 라벨 생성 (예: "05.05 ~ 05.11 주간 랭킹")
        DateTimeFormatter week = DateTimeFormatter.ofPattern("MM.dd");

        // ✅ 수정2: "주간랭킹" → " 주간 랭킹" 공백 추가
        String weekLabel = monday.format(week) + " ~ " + sunday.format(week) + " 주간 랭킹";

        model.addAttribute("rankings", rankings);
        model.addAttribute("weekLabel", weekLabel);

        log.info("{}.ranking End!", this.getClass().getName());
        return "ranking/ranking";
    }
}