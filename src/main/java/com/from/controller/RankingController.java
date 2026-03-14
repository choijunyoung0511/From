package com.from.controller;

import com.from.dto.ranking.RankingDto;
import com.from.mapper.RankingMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class RankingController {

    private final RankingMapper rankingMapper;

    @GetMapping("/ranking")
    public String ranking(HttpSession session, Model model) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";

        List<RankingDto> rankings = rankingMapper.getWeeklyRanking();

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        String weekLabel = monday.format(DateTimeFormatter.ofPattern("MM.dd")) +
                " ~ " + sunday.format(DateTimeFormatter.ofPattern("MM.dd")) + " 주간 랭킹";

        model.addAttribute("rankings", rankings);
        model.addAttribute("weekLabel", weekLabel);
        return "ranking/ranking";
    }
}