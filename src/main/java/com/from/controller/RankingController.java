package com.from.controller;

import com.from.dto.RankingDto;
import com.from.mapper.RankingMapper;
import jakarta.servlet.http.HttpSession;
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

    private final RankingMapper rankingMapper;

    /**
     * 주간 랭킹 화면을 반환한다.
     * weekly_rankings 테이블에서 이번 주 순위를 조회하여 Model 에 담는다.
     *
     * @param session 로그인 세션 확인용
     * @param model   랭킹 목록과 주간 라벨을 담아 뷰에 전달
     * @return ranking/ranking 템플릿
     */
    @GetMapping("/ranking")
    public String ranking(HttpSession session, Model model) {
        log.info("{}.ranking Start!", this.getClass().getName());

        // 세션에서 로그인 ID 확인 (인터셉터가 먼저 처리하지만 이중 방어)
        if (session.getAttribute("SS_USER_ID") == null) {
            return "redirect:/user/login";
        }

        // 이번 주 랭킹 조회
        List<RankingDto> rankings = rankingMapper.getWeeklyRanking();

        // 이번 주 월요일~일요일 날짜 라벨 생성 (예: "04.07 ~ 04.13 주간 랭킹")
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
