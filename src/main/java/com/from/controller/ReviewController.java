package com.from.controller;

import com.from.domain.Book;
import com.from.dto.user.SessionUser;
import com.from.mapper.BookMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final BookMapper bookMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String openaiApiKey;

    // ── AI 독후감 생성 페이지 ─────────────────────────────────────────────────
    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        if (loginUser != null) {
            List<Book> books = bookMapper.findBooksByUserId(loginUser.getUserId());
            model.addAttribute("myBooks", books);
        }
        return "review/create";
    }

    // ── 독후감 생성 (GPT 호출) ────────────────────────────────────────────────
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

        Map<String, Object> result = new HashMap<>();
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");

        if (loginUser == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            // 1. bookId로 책 정보 조회
            List<Book> books = bookMapper.findBooksByUserId(loginUser.getUserId());
            Book selectedBook = books.stream()
                    .filter(b -> b.getBookId().equals(bookId))
                    .findFirst()
                    .orElse(null);

            if (selectedBook == null) {
                result.put("success", false);
                result.put("message", "책 정보를 찾을 수 없습니다.");
                return result;
            }

            // 2. 톤 한글 변환
            String toneKr = switch (tone) {
                case "FORMAL"     -> "격식체";
                case "CASUAL"     -> "친근하게";
                case "EMOTIONAL"  -> "감성적으로";
                case "ANALYTICAL" -> "분석적으로";
                default           -> "자연스럽게";
            };

            // 3. GPT 프롬프트 생성
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
                    """.formatted(
                    selectedBook.getTitle(),
                    selectedBook.getAuthor(),
                    emphasis,
                    toneKr
            );

            // 4. GPT API 호출
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("max_tokens", 1500);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            String gptResponse = WebClient.create("https://api.openai.com")
                    .post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 5. GPT 응답 파싱
            JsonNode root = objectMapper.readTree(gptResponse);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            // 6. 결과 반환 (세션에 저장해서 결과 페이지에서 사용)
            session.setAttribute("reviewContent", content);
            session.setAttribute("reviewBookTitle", selectedBook.getTitle());
            session.setAttribute("reviewBookAuthor", selectedBook.getAuthor());
            session.setAttribute("reviewPaperId", paperId);
            session.setAttribute("reviewDeliveryDate", deliveryDate);
            session.setAttribute("reviewDeliveryTime", deliveryTime);

            result.put("success", true);
            result.put("reviewId", "result");

        } catch (Exception e) {
            log.error("독후감 생성 오류", e);
            result.put("success", false);
            result.put("message", "독후감 생성 중 오류가 발생했습니다.");
        }

        return result;
    }

    // ── AI 독후감 결과 페이지 ─────────────────────────────────────────────────
    @GetMapping("/result")
    public String result(HttpSession session, Model model) {
        model.addAttribute("reviewContent",      session.getAttribute("reviewContent"));
        model.addAttribute("reviewBookTitle",    session.getAttribute("reviewBookTitle"));
        model.addAttribute("reviewBookAuthor",   session.getAttribute("reviewBookAuthor"));
        model.addAttribute("reviewPaperId",      session.getAttribute("reviewPaperId"));
        model.addAttribute("reviewDeliveryDate", session.getAttribute("reviewDeliveryDate"));
        model.addAttribute("reviewDeliveryTime", session.getAttribute("reviewDeliveryTime"));
        return "review/result";
    }

    // ── 책 속 주인공 이미지 생성 페이지 ──────────────────────────────────────
    @GetMapping("/image")
    public String image() {
        return "review/image";
    }

    // ── 책 추천 페이지 ────────────────────────────────────────────────────────
    @GetMapping("/recommend")
    public String recommend() {
        return "review/recommend";
    }

    // ── 등록된 책 목록 조회 ───────────────────────────────────────────────────
    @GetMapping("/books")
    @ResponseBody
    public List<Map<String, Object>> getUserBooks(HttpSession session) {
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        if (loginUser == null) return new ArrayList<>();

        List<Book> books = bookMapper.findBooksByUserId(loginUser.getUserId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Book book : books) {
            Map<String, Object> bookData = new HashMap<>();
            bookData.put("title", book.getTitle());
            bookData.put("author", book.getAuthor());
            bookData.put("emoji", getEmojiForBook(book.getTitle()));
            result.add(bookData);
        }
        return result;
    }

    // ── 이미지 생성 요청 ──────────────────────────────────────────────────────
    @PostMapping("/image/generate")
    @ResponseBody
    public Map<String, Object> generateImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("bookTitle") String bookTitle,
            @RequestParam("style") String style,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");

        if (loginUser == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            result.put("success", true);
            result.put("imageUrl", "");
            result.put("description", getDescriptionForBook(bookTitle));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "이미지 생성 중 오류가 발생했습니다.");
        }
        return result;
    }

    // ── 이미지 히스토리 조회 ──────────────────────────────────────────────────
    @GetMapping("/image/history")
    @ResponseBody
    public List<Map<String, Object>> getImageHistory(HttpSession session) {
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        if (loginUser == null) return new ArrayList<>();
        return new ArrayList<>();
    }

    private String getEmojiForBook(String title) {
        if (title.contains("사랑")) return "📖";
        if (title.contains("채식")) return "🌿";
        if (title.contains("코스모스")) return "🌌";
        if (title.contains("데미안")) return "🦋";
        if (title.contains("1984")) return "👁";
        if (title.contains("사피엔스")) return "🦴";
        return "📚";
    }

    private String getDescriptionForBook(String title) {
        if (title.contains("사랑")) return "사랑의 철학적 의미를 탐구하는 따뜻하고 서정적인 분위기로, 부드러운 빛과 꽃들이 가득한 배경 속에 당신을 표현했습니다.";
        if (title.contains("채식")) return "한강의 몽환적이고 신비로운 세계관을 담아, 숲 속 나무와 빛이 어우러진 고요한 장면 속 주인공으로 재해석했습니다.";
        if (title.contains("코스모스")) return "광대한 우주를 배경으로, 별빛이 쏟아지는 밤하늘 아래 서 있는 당신의 모습을 담았습니다.";
        if (title.contains("데미안")) return "내면의 각성을 상징하는 나비와 빛의 이미지를 더해, 성장과 탐구의 여정 속 주인공으로 표현했습니다.";
        if (title.contains("1984")) return "디스토피아적 분위기 속에서도 빛을 향해 나아가는 강인한 존재로, 회색 도시를 배경으로 표현했습니다.";
        if (title.contains("사피엔스")) return "인류 역사의 장대한 흐름 속에서, 문명의 시작점에 서 있는 탐험가의 모습으로 재해석했습니다.";
        return "책 속 주인공으로 재탄생한 당신의 모습입니다.";
    }
}