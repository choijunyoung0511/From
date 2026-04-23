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

        // weekly_rankings 테이블에서 현재 랭킹 조회
        List<RankingDto> rankings = rankingService.getWeeklyRankings();

        // 이번 주 월요일과 일요일 날짜를 계산하여 "MM.dd ~ MM.dd 주간 랭킹" 형태의 레이블 생성
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        String weekLabel = monday.format(DateTimeFormatter.ofPattern("MM.dd")) +
                " ~ " + sunday.format(DateTimeFormatter.ofPattern("MM.dd")) + " 주간 랭킹";

        model.addAttribute("rankings",  rankings);
        model.addAttribute("weekLabel", weekLabel);

        log.info("{}.ranking End!", this.getClass().getName());
        return "ranking/ranking";
    }
}