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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    @Value("${aladin.api.key}")
    private String apiKey;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final BookMapper bookMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/register")
    public String register(HttpSession session) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";
        return "book/register";
    }

    @GetMapping("/recommend")
    public String recommendPage(HttpSession session) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";
        return "book/recommend";
    }

    // ── AI 맞춤 추천 ──────────────────────────────────────────────────────────
    @GetMapping("/ai-recommend")
    @ResponseBody
    public ResponseEntity<?> getAiRecommend(HttpSession session) {
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        if (loginUser == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        try {
            // 1. 유저가 등록한 책 목록 조회
            List<Book> userBooks = bookMapper.findBooksByUserId(loginUser.getUserId());

            if (userBooks == null || userBooks.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            // 2. 책 목록 → 문자열 변환
            String bookList = userBooks.stream()
                    .map(b -> b.getTitle() + " - " + b.getAuthor())
                    .collect(Collectors.joining("\n"));

            // 3. 프롬프트 생성
            String prompt = """
                    사용자가 읽은 책 목록:
                    %s
                    
                    위 책들을 바탕으로 이 사용자에게 어울리는 책 5권을 추천해줘.
                    반드시 아래 JSON 배열 형식으로만 반환하고 다른 말은 절대 하지 마.
                    [{"title": "책제목", "author": "저자", "reason": "추천이유 2~3문장"}]
                    """.formatted(bookList);

            // 4. GPT API 호출
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("max_tokens", 1000);
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

            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            List<Map<String, String>> result = objectMapper.readValue(
                    content,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            // 6. 알라딘 API로 표지 이미지 검색해서 추가
            for (Map<String, String> rec : result) {
                String cover = searchCoverFromAladin(rec.get("title"));
                rec.put("cover", cover);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("AI 추천 오류", e);
            return ResponseEntity.status(500).body("추천 생성 중 오류가 발생했습니다.");
        }
    }

    // ── 알라딘에서 표지 이미지 검색 ──────────────────────────────────────────
    private String searchCoverFromAladin(String title) {
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ttb/api/ItemSearch.aspx")
                            .queryParam("TTBKey", apiKey)
                            .queryParam("Query", title)
                            .queryParam("QueryType", "Title")
                            .queryParam("MaxResults", 1)
                            .queryParam("SearchTarget", "Book")
                            .queryParam("output", "xml")
                            .queryParam("Version", "20131101")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");

            if (items.getLength() > 0) {
                return getTagValue("cover", (Element) items.item(0));
            }
        } catch (Exception e) {
            log.error("알라딘 표지 검색 오류: {}", title, e);
        }
        return "";
    }

    // ── 책 검색 ───────────────────────────────────────────────────────────────
    @GetMapping("/search")
    @ResponseBody
    public List<Map<String, String>> searchBooks(@RequestParam String query) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ttb/api/ItemSearch.aspx")
                            .queryParam("TTBKey", apiKey)
                            .queryParam("Query", query)
                            .queryParam("QueryType", "Title")
                            .queryParam("MaxResults", 10)
                            .queryParam("SearchTarget", "Book")
                            .queryParam("output", "xml")
                            .queryParam("Version", "20131101")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                Map<String, String> book = new HashMap<>();
                book.put("title", getTagValue("title", item));
                book.put("author", getTagValue("author", item));
                book.put("cover", getTagValue("cover", item));
                book.put("isbn", getTagValue("isbn13", item));
                result.add(book);
            }
        } catch (Exception e) {
            log.error("알라딘 API 오류", e);
        }
        return result;
    }

    // ── 책 등록 ───────────────────────────────────────────────────────────────
    @PostMapping("/register")
    @ResponseBody
    public Map<String, Object> registerBook(@RequestParam String title,
                                            @RequestParam String author,
                                            @RequestParam(required = false) String cover,
                                            @RequestParam(required = false) String isbn,
                                            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");

        if (loginUser == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            Book book = bookMapper.findByTitleAndAuthor(title, author);

            if (book == null) {
                book = new Book();
                book.setTitle(title);
                book.setAuthor(author);
                book.setCoverImage(cover);
                bookMapper.insertBook(book);
            }

            bookMapper.insertUserBook(loginUser.getUserId(), book.getBookId());

            result.put("success", true);
            result.put("message", "등록되었습니다.");

        } catch (Exception e) {
            log.error("책 등록 오류", e);
            result.put("success", false);
            result.put("message", "등록 중 오류가 발생했습니다.");
        }
        return result;
    }

    // ── 베스트셀러 ────────────────────────────────────────────────────────────
    @GetMapping("/bestseller")
    @ResponseBody
    public List<Map<String, String>> getBestseller() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ttb/api/ItemList.aspx")
                            .queryParam("TTBKey", apiKey)
                            .queryParam("QueryType", "Bestseller")
                            .queryParam("MaxResults", 10)
                            .queryParam("SearchTarget", "Book")
                            .queryParam("output", "xml")
                            .queryParam("Version", "20131101")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                Map<String, String> book = new HashMap<>();
                book.put("title", getTagValue("title", item));
                book.put("author", getTagValue("author", item));
                book.put("cover", getTagValue("cover", item));
                book.put("isbn", getTagValue("isbn13", item));
                result.add(book);
            }
        } catch (Exception e) {
            log.error("베스트셀러 API 오류", e);
        }
        return result;
    }

    private String getTagValue(String tag, Element element) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() > 0 && list.item(0).getChildNodes().getLength() > 0) {
            return list.item(0).getChildNodes().item(0).getNodeValue();
        }
        return "";
    }
}