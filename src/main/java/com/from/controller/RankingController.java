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
 * /ranking 경로는 LoginInterceptor 가 인증을 검사한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RankingController {

    private final IRankingService rankingService;

    @GetMapping("/ranking")
    public String ranking(Model model) {
        log.info("{}.ranking Start!", this.getClass().getName());

        List<RankingDto> rankings = rankingService.getWeeklyRankings();

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