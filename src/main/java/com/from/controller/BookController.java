package com.from.controller;

import com.from.dto.BookSearchDTO;
import com.from.dto.MsgDTO;
import com.from.dto.RecommendSectionDTO;
import com.from.service.IAladinService;
import com.from.service.IBookService;

import com.from.service.impl.RankingRedisService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * 맞춤 도서 추천 (Aladin API 기반).
     * 사용자가 읽은 책의 카테고리를 분석하여 알라딘 API로 직접 검색한다.
     * GPT 호출 없이 Aladin 태그 검색만 사용 — 비용 절감 + 응답 빠름.
     */
    @GetMapping("/ai-recommend")
    @ResponseBody
    public ResponseEntity<?> getAiRecommend(HttpSession session) {
        log.info("{}.getAiRecommend Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build());

        List<BookSearchDTO> readBooks = bookService.findByUserId(userId);
        if (readBooks.isEmpty()) return ResponseEntity.ok(List.of());

        Set<String> readTitles = readBooks.stream()
                .map(b -> b.title().toLowerCase().trim())
                .collect(Collectors.toSet());

        // 읽은 책의 카테고리 빈도 상위 3개 추출
        List<String> topCategories = readBooks.stream()
                .map(b -> extractLeafCategory(b.category()))
                .filter(c -> !c.isBlank())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        if (topCategories.isEmpty()) topCategories = List.of("소설", "자기계발", "에세이");

        // Aladin API로 카테고리별 검색 → 이미 읽은 책 제외 → 5권 반환
        List<Map<String, String>> result = new ArrayList<>();
        for (String category : topCategories) {
            if (result.size() >= 5) break;
            List<BookSearchDTO> found = aladinService.searchBooks(category, "Keyword");
            for (BookSearchDTO b : found) {
                if (result.size() >= 5) break;
                if (!readTitles.contains(b.title().toLowerCase().trim())) {
                    Map<String, String> rec = new java.util.HashMap<>();
                    rec.put("title",  b.title());
                    rec.put("author", b.author());
                    rec.put("cover",  b.cover() != null ? b.cover() : "");
                    rec.put("reason", "'" + category + "' 카테고리 기반 추천");
                    result.add(rec);
                    readTitles.add(b.title().toLowerCase().trim()); // 중복 방지
                }
            }
        }

        log.info("{}.getAiRecommend End! - {}권", this.getClass().getName(), result.size());
        return ResponseEntity.ok(result);
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
                dto = MsgDTO.builder().result(1).msg("등록되었습니다.").bookId(book.bookId()).build();
            } else {
                dto = MsgDTO.builder().result(0).msg("이미 등록된 책입니다.").bookId(book.bookId()).build();
            }

        } catch (Exception e) {
            log.error("책 등록 오류", e);
            dto = MsgDTO.builder().result(0).msg("등록 중 오류가 발생했습니다.").build();
        }

        log.info("{}.registerBook End!", this.getClass().getName());
        return dto;
    }

    @PostMapping("/rating")
    @ResponseBody
    public MsgDTO saveRating(@RequestParam Long bookId,
                             @RequestParam int rating,
                             @RequestParam(required = false, defaultValue = "") String content,
                             HttpSession session) {
        log.info("{}.saveRating Start! - bookId:{}, rating:{}", this.getClass().getName(), bookId, rating);
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build();
        if (rating < 1 || rating > 5) return MsgDTO.builder().result(0).msg("별점은 1~5 사이여야 합니다.").build();
        try {
            String safeContent = content.length() > 300 ? content.substring(0, 300) : content;
            bookService.saveRating(userId, bookId, rating, safeContent);
            log.info("{}.saveRating End!", this.getClass().getName());
            return MsgDTO.builder().result(1).msg("후기가 등록되었습니다.").build();
        } catch (Exception e) {
            log.error("후기 저장 오류", e);
            return MsgDTO.builder().result(0).msg("후기 등록에 실패했습니다.").build();
        }
    }

    @GetMapping("/ratings")
    @ResponseBody
    public ResponseEntity<?> getRatings(@RequestParam String title, @RequestParam String author,
                                        HttpSession session) {
        log.info("{}.getRatings Start! - title:{}", this.getClass().getName(), title);
        try {
            String userId = (String) session.getAttribute("SS_USER_ID");
            Optional<BookSearchDTO> book = bookService.findByTitleAndAuthor(title, author);
            if (book.isEmpty()) return ResponseEntity.ok(java.util.Collections.emptyList());
            List<java.util.Map<String, Object>> ratings = bookService.getRatings(book.get().bookId(), userId);
            log.info("{}.getRatings End! - {}건", this.getClass().getName(), ratings.size());
            return ResponseEntity.ok(ratings);
        } catch (Exception e) {
            log.error("후기 조회 오류", e);
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }

    @DeleteMapping("/rating/{ratingId}")
    @ResponseBody
    public MsgDTO deleteRating(@PathVariable Long ratingId, HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build();
        boolean deleted = bookService.deleteRating(ratingId, userId);
        return deleted
            ? MsgDTO.builder().result(1).msg("후기가 삭제되었습니다.").build()
            : MsgDTO.builder().result(0).msg("삭제 권한이 없습니다.").build();
    }

    @PatchMapping("/rating/{ratingId}")
    @ResponseBody
    public MsgDTO updateMyRating(@PathVariable Long ratingId,
                                 @RequestParam int rating,
                                 @RequestParam(required = false, defaultValue = "") String content,
                                 HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build();
        boolean updated = bookService.updateMyRating(ratingId, userId, rating, content);
        return updated
            ? MsgDTO.builder().result(1).msg("후기가 수정되었습니다.").build()
            : MsgDTO.builder().result(0).msg("수정 권한이 없습니다.").build();
    }

    @GetMapping("/my-ratings")
    @ResponseBody
    public ResponseEntity<?> getMyRatings(HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(List.of());
        return ResponseEntity.ok(bookService.getMyRatings(userId));
    }

    @PostMapping("/rating/{ratingId}/like")
    @ResponseBody
    public ResponseEntity<?> toggleLike(@PathVariable Long ratingId, HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build());
        return ResponseEntity.ok(bookService.toggleLike(ratingId, userId));
    }

    @PostMapping("/rating/{ratingId}/comment")
    @ResponseBody
    public MsgDTO addComment(@PathVariable Long ratingId,
                             @RequestParam String content,
                             HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build();
        if (content == null || content.isBlank()) return MsgDTO.builder().result(0).msg("댓글 내용을 입력해주세요.").build();
        String safe = content.length() > 500 ? content.substring(0, 500) : content;
        bookService.addComment(ratingId, userId, safe);
        return MsgDTO.builder().result(1).msg("댓글이 등록되었습니다.").build();
    }

    @GetMapping("/rating/{ratingId}/comments")
    @ResponseBody
    public ResponseEntity<?> getComments(@PathVariable Long ratingId) {
        return ResponseEntity.ok(bookService.getComments(ratingId));
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