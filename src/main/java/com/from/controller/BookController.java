package com.from.controller;

import com.from.dto.BookSearchDTO;
import com.from.dto.MsgDTO;
import com.from.dto.RecommendSectionDTO;
import com.from.service.IAladinService;
import com.from.service.IBookService;
import com.from.service.IReviewService;
import com.from.service.impl.RankingRedisService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final IBookService bookService;
    private final IAladinService aladinService;
    private final IReviewService reviewService;
    private final RankingRedisService rankingRedisService;

    @GetMapping("/register")
    public String register(HttpSession session) {
        log.info("{}.register Start!", this.getClass().getName());
        if (session.getAttribute("SS_USER_ID") == null) return "redirect:/user/login";
        log.info("{}.register End!", this.getClass().getName());
        return "book/register";
    }

    @GetMapping("/recommend")
    public String recommendPage(HttpSession session) {
        log.info("{}.recommendPage Start!", this.getClass().getName());
        if (session.getAttribute("SS_USER_ID") == null) return "redirect:/user/login";
        log.info("{}.recommendPage End!", this.getClass().getName());
        return "book/recommend";
    }

    /**
     * 섹션형 맞춤 추천 (작가의 다른 책 + 카테고리 기반)
     * 알라딘 API를 병렬로 4회 호출하여 List<RecommendSectionDTO>를 반환한다.
     */
    @GetMapping("/section-recommend")
    @ResponseBody
    public ResponseEntity<?> getSectionRecommend(HttpSession session) {
        log.info("{}.getSectionRecommend Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        List<BookSearchDTO> readBooks = bookService.findByUserId(userId);
        if (readBooks.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // 이미 읽은 책 제목 집합 (소문자 정규화, 중복 제거용)
        Set<String> readTitles = readBooks.stream()
                .map(b -> b.title().toLowerCase().trim())
                .collect(Collectors.toSet());

        // 최근 등록 순 작가 상위 2명
        List<String> topAuthors = readBooks.stream()
                .sorted(Comparator.comparing(BookSearchDTO::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(b -> cleanAuthor(b.author()))
                .filter(a -> !a.isBlank())
                .distinct()
                .limit(2)
                .toList();

        // 카테고리 빈도 상위 2개
        List<String> topCategories = readBooks.stream()
                .map(b -> extractLeafCategory(b.category()))
                .filter(c -> !c.isBlank())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(2)
                .map(Map.Entry::getKey)
                .toList();

        // 알라딘 API 병렬 호출
        List<CompletableFuture<RecommendSectionDTO>> futures = new ArrayList<>();

        for (String author : topAuthors) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<BookSearchDTO> results = aladinService.searchBooks(author, "Author");
                List<BookSearchDTO> filtered = results.stream()
                        .filter(b -> !readTitles.contains(b.title().toLowerCase().trim()))
                        .limit(6)
                        .toList();
                return RecommendSectionDTO.builder()
                        .type("author")
                        .label(author + " 작가의 다른 책")
                        .icon("✍️")
                        .books(filtered)
                        .build();
            }));
        }

        for (String category : topCategories) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                List<BookSearchDTO> results = aladinService.searchBooks(category, "Keyword");
                List<BookSearchDTO> filtered = results.stream()
                        .filter(b -> !readTitles.contains(b.title().toLowerCase().trim()))
                        .limit(6)
                        .toList();
                return RecommendSectionDTO.builder()
                        .type("category")
                        .label(category + " 추천")
                        .icon("📚")
                        .books(filtered)
                        .build();
            }));
        }

        List<RecommendSectionDTO> sections = futures.stream()
                .map(f -> {
                    try { return f.get(30, TimeUnit.SECONDS); }
                    catch (Exception e) {
                        log.error("섹션 추천 조회 오류", e);
                        return null;
                    }
                })
                .filter(s -> s != null && !s.books().isEmpty())
                .toList();

        log.info("{}.getSectionRecommend End! - {}섹션", this.getClass().getName(), sections.size());
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/ai-recommend")
    @ResponseBody
    public ResponseEntity<?> getAiRecommend(HttpSession session) {
        log.info("{}.getAiRecommend Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build());

        try {
            List<Map<String, String>> result = reviewService.getAiRecommendations(userId);
            log.info("{}.getAiRecommend End!", this.getClass().getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI 추천 오류", e);
            return ResponseEntity.status(500).body(MsgDTO.builder().result(0).msg("추천 생성 중 오류가 발생했습니다.").build());
        }
    }

    @GetMapping("/search")
    @ResponseBody
    public List<BookSearchDTO> searchBooks(@RequestParam String query,
                                           @RequestParam(defaultValue = "Keyword") String type) {
        log.info("{}.searchBooks Start! - query:{}, type:{}", this.getClass().getName(), query, type);
        List<BookSearchDTO> result = aladinService.searchBooks(query, type);
        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    @GetMapping("/bestseller")
    @ResponseBody
    public List<BookSearchDTO> getBestseller() {
        log.info("{}.getBestseller Start!", this.getClass().getName());
        List<BookSearchDTO> result = aladinService.getBestseller();
        log.info("{}.getBestseller End!", this.getClass().getName());
        return result;
    }

    @PostMapping("/register")
    @ResponseBody
    public MsgDTO registerBook(@RequestParam String title,
                               @RequestParam String author,
                               @RequestParam(required = false, defaultValue = "") String cover,
                               @RequestParam(required = false, defaultValue = "") String description,
                               @RequestParam(required = false, defaultValue = "") String category,
                               HttpSession session) {
        log.info("{}.registerBook Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build();

        MsgDTO dto;
        try {
            Optional<BookSearchDTO> existing = bookService.findByTitleAndAuthor(title, author);
            BookSearchDTO book = existing.orElseGet(() -> bookService.save(title, author, cover, description, category));

            boolean registered = bookService.saveUserBook(userId, book.bookId());

            if (registered) {
                rankingRedisService.incrementBookCount(userId);
                dto = MsgDTO.builder().result(1).msg("등록되었습니다.").build();
            } else {
                dto = MsgDTO.builder().result(0).msg("이미 등록된 책입니다.").build();
            }

        } catch (Exception e) {
            log.error("책 등록 오류", e);
            dto = MsgDTO.builder().result(0).msg("등록 중 오류가 발생했습니다.").build();
        }

        log.info("{}.registerBook End!", this.getClass().getName());
        return dto;
    }

    /** "김훈 (지은이)" → "김훈", 여러 저자면 첫 번째만 */
    private String cleanAuthor(String raw) {
        if (raw == null || raw.isBlank()) return "";
        int parenIdx = raw.indexOf('(');
        String name = (parenIdx > 0 ? raw.substring(0, parenIdx) : raw).trim();
        int commaIdx = name.indexOf(',');
        return (commaIdx > 0 ? name.substring(0, commaIdx) : name).trim();
    }

    /** "국내도서>소설/시/희곡>한국소설" → "한국소설" */
    private String extractLeafCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return "";
        String[] parts = categoryName.split(">");
        return parts[parts.length - 1].trim();
    }
}