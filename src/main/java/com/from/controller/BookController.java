package com.from.controller;

import com.from.service.IAladinService;
import com.from.service.IBookService;
import com.from.service.IReviewService;
import com.from.repository.entity.BookEntity;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final IBookService bookService;
    private final IAladinService aladinService;
    private final IReviewService reviewService;

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

    @GetMapping("/ai-recommend")
    @ResponseBody
    public ResponseEntity<?> getAiRecommend(HttpSession session) {
        log.info("{}.getAiRecommend Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body("로그인이 필요합니다.");

        try {
            List<Map<String, String>> result = reviewService.getAiRecommendations(userId);
            log.info("{}.getAiRecommend End!", this.getClass().getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI 추천 오류", e);
            return ResponseEntity.status(500).body("추천 생성 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/search")
    @ResponseBody
    public List<Map<String, String>> searchBooks(@RequestParam String query,
                                                  @RequestParam(defaultValue = "Keyword") String type) {
        log.info("{}.searchBooks Start! - query:{}, type:{}", this.getClass().getName(), query, type);
        List<Map<String, String>> result = aladinService.searchBooks(query, type);
        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    @GetMapping("/bestseller")
    @ResponseBody
    public List<Map<String, String>> getBestseller() {
        log.info("{}.getBestseller Start!", this.getClass().getName());
        List<Map<String, String>> result = aladinService.getBestseller();
        log.info("{}.getBestseller End!", this.getClass().getName());
        return result;
    }

    @PostMapping("/register")
    @ResponseBody
    public Map<String, Object> registerBook(@RequestParam String title,
                                            @RequestParam String author,
                                            @RequestParam(required = false) String cover,
                                            HttpSession session) {
        log.info("{}.registerBook Start!", this.getClass().getName());

        Map<String, Object> result = new HashMap<>();
        String userId = (String) session.getAttribute("SS_USER_ID");

        if (userId == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            Optional<BookEntity> existing = bookService.findByTitleAndAuthor(title, author);
            BookEntity book = existing.orElseGet(() ->
                    bookService.save(BookEntity.builder()
                            .title(title)
                            .author(author)
                            .coverImage(cover)
                            .build())
            );

            boolean registered = bookService.saveUserBook(userId, book.getBookId());

            if (!registered) {
                result.put("success", false);
                result.put("message", "이미 등록된 책입니다.");
                return result;
            }

            result.put("success", true);
            result.put("message", "등록되었습니다.");

        } catch (Exception e) {
            log.error("책 등록 오류", e);
            result.put("success", false);
            result.put("message", "등록 중 오류가 발생했습니다.");
        }

        log.info("{}.registerBook End!", this.getClass().getName());
        return result;
    }
}