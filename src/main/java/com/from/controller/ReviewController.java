package com.from.controller;

import com.from.domain.Book;
import com.from.domain.BookReview;
import com.from.dto.user.SessionUser;
import com.from.mapper.BookMapper;
import com.from.mapper.BookReviewMapper;
import com.from.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final BookMapper bookMapper;
    private final BookReviewMapper bookReviewMapper;
    private final UserMapper userMapper;

    // ===== 독후감 생성 화면 =====
    @GetMapping
    public String create(HttpSession session, Model model) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";

        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        List<Book> myBooks = bookMapper.findBooksByUserId(loginUser.getUserId());
        model.addAttribute("myBooks", myBooks);

        return "review/create";
    }

    // ===== 독후감 생성 (Ajax) =====
    @PostMapping("/generate")
    @ResponseBody
    public Map<String, Object> generate(
            @RequestParam String bookId,
            @RequestParam String emphasis,
            @RequestParam String tone,
            @RequestParam String deliveryDate,
            @RequestParam String deliveryTime,
            @RequestParam Integer paperId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");

        if (loginUser == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            // 톤 텍스트 변환
            String toneText = switch (tone) {
                case "FORMAL"     -> "격식체로 정중하게";
                case "CASUAL"     -> "친근하고 편안하게";
                case "EMOTIONAL"  -> "감성적이고 따뜻하게";
                case "ANALYTICAL" -> "분석적이고 논리적으로";
                default           -> "자연스럽게";
            };

            // 책 정보 조회 (제목 프롬프트에 사용)
            String bookTitle = bookId;
            try {
                Long bookIdLong = Long.parseLong(bookId);
                Book book = bookMapper.findBooksByUserId(loginUser.getUserId())
                        .stream()
                        .filter(b -> b.getBookId().equals(bookIdLong))
                        .findFirst()
                        .orElse(null);
                if (book != null) bookTitle = book.getTitle() + " (저자: " + book.getAuthor() + ")";
            } catch (NumberFormatException ignored) {}

            // OpenAI 프롬프트
            String prompt = String.format("""
                    당신은 독서 편지를 써주는 따뜻한 AI 작가입니다.
                    아래 책을 읽은 '지금의 나'가 미래의 나에게 보내는 진심 어린 독서 편지를 써주세요.
                    
                    책: %s
                    강조할 내용: %s
                    편지 톤: %s
                    편지 전달 예정일: %s %s
                    
                    작성 조건:
                    - 반드시 편지 형식으로 작성 (예: "미래의 나에게," 로 시작, "지금의 너로부터" 등으로 마무리)
                    - 400~600자 내외
                    - 책의 제목이나 구절을 자연스럽게 편지 안에 녹여낼 것
                    - 강조할 내용을 억지스럽지 않게, 마음에서 우러나오는 말투로 전달
                    - %s에 이 편지를 열어보는 순간의 설렘과 감동을 담을 것
                    - 그 날의 나에게 "지금 잘 하고 있어", "이 책이 네 곁에 있었어" 같은 따뜻한 위로와 응원을 담을 것
                    - 일기나 SNS 글투가 아닌, 손으로 쓴 편지처럼 진심 어린 문체로 작성
                    - 너무 교훈적이거나 설교하는 느낌 금지. 친한 친구에게 쓰듯 자연스럽게
                """, bookTitle, emphasis, toneText, deliveryDate, deliveryTime, deliveryDate);

            // OpenAI API 호출
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("max_tokens", 1000);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            Map response = WebClient.create("https://api.openai.com")
                    .post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            // 응답 파싱
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            String reviewContent = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            log.info("=== 독후감 생성 완료 ===\n{}", reviewContent);

            // DB 저장
            BookReview review = new BookReview();
            review.setUserId(loginUser.getUserId());
            review.setBookId(Long.parseLong(bookId));
            review.setPaperId(paperId);
            review.setEmphasisContent(emphasis);
            review.setTone(tone);
            review.setDeliveryDate(LocalDate.parse(deliveryDate));
            review.setDeliveryTime(LocalTime.parse(deliveryTime));
            review.setAiContent(reviewContent);
            bookReviewMapper.insertReview(review);

            log.info("독후감 DB 저장 완료 - reviewId: {}, 발송예정: {} {}",
                    review.getReviewId(), deliveryDate, deliveryTime);

            result.put("success", true);
            result.put("reviewId", review.getReviewId());

        } catch (Exception e) {
            log.error("독후감 생성 오류", e);
            result.put("success", false);
            result.put("message", "독후감 생성 중 오류가 발생했습니다.");
        }

        return result;
    }

    // ===== 독후감 결과 화면 =====
    @GetMapping("/{reviewId}")
    public String reviewDetail(@PathVariable Long reviewId,
                               HttpSession session,
                               Model model) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";

        BookReview review = bookReviewMapper.findById(reviewId);
        if (review == null) return "redirect:/review";

        // 책 정보 조회
        List<Book> myBooks = bookMapper.findBooksByUserId(
                ((SessionUser) session.getAttribute("loginUser")).getUserId());
        Book book = myBooks.stream()
                .filter(b -> b.getBookId().equals(review.getBookId()))
                .findFirst().orElse(null);

        model.addAttribute("review", review);
        model.addAttribute("book", book);
        return "review/result";
    }
}