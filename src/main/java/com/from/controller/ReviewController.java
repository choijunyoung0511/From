package com.from.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.from.domain.BookReviewDocument;
import com.from.repository.BookReviewMongoRepository;
import com.from.repository.entity.BookEntity;
import com.from.service.IBookService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 독후감 생성·결과 조회 기능을 처리하는 컨트롤러.
 * /review/** 경로는 LoginInterceptor 가 인증을 검사한다.
 */
@Slf4j
@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final IBookService bookService;
    private final BookReviewMongoRepository bookReviewMongoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String openaiApiKey;

    /**
     * AI 독후감 생성 화면을 반환한다.
     */
    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        log.info("{}.create Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId != null) {
            model.addAttribute("myBooks", bookService.findByUserId(userId));
        }

        log.info("{}.create End!", this.getClass().getName());
        return "review/create";
    }

    /**
     * GPT API 를 호출하여 AI 독후감을 생성하고 MongoDB 에 저장한다.
     */
    @PostMapping("/generate")
    @ResponseBody
    public Map<String, Object> generateReview(
            @RequestParam Long bookId,
            @RequestParam String emphasis,
            @RequestParam String tone,
            @RequestParam String deliveryDate,
            @RequestParam String deliveryTime,
            @RequestParam int paperId,
            HttpSession session) {

        log.info("{}.generateReview Start!", this.getClass().getName());

        Map<String, Object> result = new HashMap<>();
        String userId = (String) session.getAttribute("SS_USER_ID");

        if (userId == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            // 유저의 책 목록에서 선택한 책 조회
            BookEntity selectedBook = bookService.findByUserId(userId).stream()
                    .filter(b -> b.getBookId().equals(bookId))
                    .findFirst()
                    .orElse(null);

            if (selectedBook == null) {
                result.put("success", false);
                result.put("message", "책 정보를 찾을 수 없습니다.");
                return result;
            }

            String toneKr = switch (tone) {
                case "FORMAL"     -> "격식체";
                case "CASUAL"     -> "친근하게";
                case "EMOTIONAL"  -> "감성적으로";
                case "ANALYTICAL" -> "분석적으로";
                default           -> "자연스럽게";
            };

            String prompt = """
                    다음 책에 대한 독후감을 작성해주세요.

                    책 제목: %s
                    저자: %s
                    강조할 내용: %s
                    작성 톤: %s

                    조건:
                    - 미래의 나에게 보내는 편지 형식으로 작성해주세요.
                    - 700~1000자 분량으로 작성해주세요.
                    - 책의 핵심 메시지와 강조할 내용을 잘 녹여주세요.
                    - 독후감만 반환하고 다른 말은 하지 마세요.
                    """.formatted(selectedBook.getTitle(), selectedBook.getAuthor(), emphasis, toneKr);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("max_tokens", 1500);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            String gptResponse = WebClient.create("https://api.openai.com")
                    .post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(gptResponse);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            BookReviewDocument doc = new BookReviewDocument();
            doc.setUserId(userId);
            doc.setBookId(bookId);
            doc.setPaperId(paperId);
            doc.setEmphasisContent(emphasis);
            doc.setTone(tone);
            doc.setDeliveryDate(LocalDate.parse(deliveryDate));
            doc.setDeliveryTime(LocalTime.parse(deliveryTime));
            doc.setAiContent(content);
            doc.setGenerationStatus("COMPLETED");
            doc.setIsSent(0);
            doc.setBookTitle(selectedBook.getTitle());
            doc.setBookAuthor(selectedBook.getAuthor());
            bookReviewMongoRepository.save(doc);

            session.setAttribute("reviewContent",      content);
            session.setAttribute("reviewBookTitle",    selectedBook.getTitle());
            session.setAttribute("reviewBookAuthor",   selectedBook.getAuthor());
            session.setAttribute("reviewPaperId",      paperId);
            session.setAttribute("reviewDeliveryDate", deliveryDate);
            session.setAttribute("reviewDeliveryTime", deliveryTime);

            result.put("success",  true);
            result.put("reviewId", "result");

        } catch (Exception e) {
            log.error("독후감 생성 오류", e);
            result.put("success", false);
            result.put("message", "독후감 생성 중 오류가 발생했습니다.");
        }

        log.info("{}.generateReview End!", this.getClass().getName());
        return result;
    }

    /**
     * AI 독후감 결과 화면을 반환한다.
     */
    @GetMapping("/result")
    public String result(HttpSession session, Model model) {
        log.info("{}.result Start!", this.getClass().getName());
        model.addAttribute("reviewContent",      session.getAttribute("reviewContent"));
        model.addAttribute("reviewBookTitle",    session.getAttribute("reviewBookTitle"));
        model.addAttribute("reviewBookAuthor",   session.getAttribute("reviewBookAuthor"));
        model.addAttribute("reviewPaperId",      session.getAttribute("reviewPaperId"));
        model.addAttribute("reviewDeliveryDate", session.getAttribute("reviewDeliveryDate"));
        model.addAttribute("reviewDeliveryTime", session.getAttribute("reviewDeliveryTime"));
        log.info("{}.result End!", this.getClass().getName());
        return "review/result";
    }

    @GetMapping("/image")
    public String image() {
        return "review/image";
    }

    @GetMapping("/recommend")
    public String recommend() {
        return "review/recommend";
    }

    /**
     * 현재 유저가 등록한 책 목록을 JSON 으로 반환한다.
     */
    @GetMapping("/books")
    @ResponseBody
    public List<Map<String, Object>> getUserBooks(HttpSession session) {
        log.info("{}.getUserBooks Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (BookEntity book : bookService.findByUserId(userId)) {
            Map<String, Object> bookData = new HashMap<>();
            bookData.put("title",  book.getTitle());
            bookData.put("author", book.getAuthor());
            bookData.put("emoji",  getEmojiForBook(book.getTitle()));
            result.add(bookData);
        }

        log.info("{}.getUserBooks End!", this.getClass().getName());
        return result;
    }

    private String getEmojiForBook(String title) {
        if (title.contains("사랑"))    return "📖";
        if (title.contains("채식"))    return "🌿";
        if (title.contains("코스모스")) return "🌌";
        if (title.contains("데미안"))  return "🦋";
        if (title.contains("1984"))    return "👁";
        if (title.contains("사피엔스")) return "🦴";
        return "📚";
    }
}