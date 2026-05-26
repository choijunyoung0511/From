package com.from.controller;

import com.from.dto.RankingDto;
import com.from.service.impl.RankingRedisService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

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

    //실제 주간 랭킹 순이 + 상위 몇 %인지 반환, 레디스 sortedSet 기반 실제데이터
    @GetMapping("/ranking/my-rank")
    @ResponseBody
    public ResponseEntity<?> getMyRank(HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).build();

        List<RankingDto> rankings = rankingRedisService.getWeeklyRankings();
        int total = rankings.size();

        return rankings.stream()
            .filter(r -> r.userId().equals(userId))
            .findFirst()
            .map(r -> {
                // 상위 % = 내 순위 / 전체 인원 × 100 (최소 1%)
                // 상항 연산자 전체 사용자수가 0보다 크면 계산함
                // (double) r.rankPosition() / total * 100는 내 랭킹 계산, double로 강제 형변환 안하면 정수 나눗셈됨
                int pct = total > 0 ? Math.max(1, (int) Math.ceil((double) r.rankPosition() / total * 100)) : 0;
                return ResponseEntity.ok(Map.of(
                    "rank",        r.rankPosition(),
                    "total",       total,
                    "pct",         pct,
                    "weeklyBooks", r.weeklyBookCount()
                ));
            })
            .orElse(ResponseEntity.ok(Map.of("rank", 0, "total", total, "pct", 0, "weeklyBooks", 0)));
    }
}