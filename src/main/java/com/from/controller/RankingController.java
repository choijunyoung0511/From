// RankingController.java - 주간 랭킹 화면을 처리하는 컨트롤러
// Redis Sorted Set 기반 RankingRedisService 를 직접 사용
// IRankingService 래퍼 제거 — 불필요한 위임 계층 단순화
package com.from.controller;

import com.from.dto.RankingDto;                          // 랭킹 데이터 DTO
import com.from.service.impl.RankingRedisService;        // Redis Sorted Set 기반 랭킹 서비스
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;                   // final 필드 기반 생성자 자동 생성
import lombok.extern.slf4j.Slf4j;                        // SLF4J 로거 자동 생성
import org.springframework.http.ResponseEntity;          // HTTP 상태코드 포함 응답 객체
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;                     // 템플릿에 데이터 전달용
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.time.DayOfWeek;                              // 요일 상수 (MONDAY 등)
import java.time.LocalDate;                              // 날짜 처리
import java.time.format.DateTimeFormatter;               // 날짜 포맷 지정
import java.util.List;

@Slf4j                   // log.info(), log.error() 등 로거 사용 가능하게 해주는 어노테이션
@Controller              // Spring MVC 컨트롤러로 등록 (View 이름 반환 가능)
@RequiredArgsConstructor // final 필드를 인자로 받는 생성자 자동 생성 (의존성 주입)
public class RankingController {

    private final RankingRedisService rankingRedisService; // Redis 기반 주간 랭킹 서비스

    // [GET] /ranking - 주간 랭킹 페이지 반환
    // Redis 에서 이번 주 랭킹 목록을 조회하고 주간 날짜 범위 라벨을 생성하여 템플릿에 전달
    @GetMapping("/ranking")
    public String ranking(Model model, HttpSession session) {
        log.info("{}.ranking Start!", this.getClass().getName());

        List<RankingDto> rankings = rankingRedisService.getWeeklyRankings();

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM.dd");
        String weekLabel = monday.format(fmt) + " ~ " + sunday.format(fmt) + " 주간 랭킹";

        String userId = (String) session.getAttribute("SS_USER_ID");
        boolean myRankFound = userId != null && rankings.stream().anyMatch(r -> r.userId().equals(userId));

        model.addAttribute("rankings",    rankings);
        model.addAttribute("weekLabel",   weekLabel);
        model.addAttribute("myRankFound", myRankFound);

        log.info("{}.ranking End! - {}명", this.getClass().getName(), rankings.size());
        return "ranking/ranking";
    }

    // [GET] /ranking/my-rank - 내 주간 랭킹 순위 + 상위 몇 % 인지 반환
    // Redis Sorted Set 기반 실제 데이터 사용
    @GetMapping("/ranking/my-rank")
    @ResponseBody // 반환값을 JSON 으로 직렬화하여 응답 body 에 담음
    public ResponseEntity<?> getMyRank(HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID"); // 세션에서 로그인 사용자 ID 추출
        if (userId == null) return ResponseEntity.status(401).build(); // 비로그인 시 401 반환

        List<RankingDto> rankings = rankingRedisService.getWeeklyRankings(); // 전체 주간 랭킹 목록 조회
        int total = rankings.size(); // 전체 랭킹 참여 인원 수

        return rankings.stream()
                .filter(r -> r.userId().equals(userId)) // 전체 랭킹 중 내 데이터만 필터링
                .findFirst()                            // 첫 번째 결과 (내 랭킹) 가져오기
                .map(r -> {
                    // 상위 % 계산: 내 순위 / 전체 인원 × 100 (최소 1%)
                    // total > 0 조건: 전체 사용자 수가 0 이면 나누기 불가이므로 먼저 확인
                    // (double) 강제 형변환: 정수끼리 나누면 소수점이 버려지므로 double 로 변환하여 정확한 % 계산
                    // Math.ceil: 소수점 올림 (예: 1.1% → 2%)
                    // Math.max(1, ...): 계산 결과가 0 이 나와도 최소 1% 보장
                    int pct = total > 0 ? Math.max(1, (int) Math.ceil((double) r.rankPosition() / total * 100)) : 0;
                    return ResponseEntity.ok(Map.of(
                            "rank",        r.rankPosition(),   // 내 순위
                            "total",       total,              // 전체 참여 인원
                            "pct",         pct,                // 상위 몇 %
                            "weeklyBooks", r.weeklyBookCount() // 이번 주 읽은 책 수
                    ));
                })
                // 랭킹 목록에 내 데이터가 없으면 (이번 주 등록한 책이 없으면) 기본값 반환
                .orElse(ResponseEntity.ok(Map.of("rank", 0, "total", total, "pct", 0, "weeklyBooks", 0)));
    }
}