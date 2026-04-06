package com.from.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.from.domain.Book;
import com.from.mapper.BookMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 책 검색·등록·AI 추천 기능을 처리하는 컨트롤러.
 * /book/** 경로는 LoginInterceptor 가 인증을 검사한다.
 */
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

    /**
     * 책 등록 화면을 반환한다.
     *
     * @param session 로그인 세션
     * @return book/register 템플릿
     */
    @GetMapping("/register")
    public String register(HttpSession session) {
        log.info("{}.register Start!", this.getClass().getName());

        if (session.getAttribute("SS_USER_ID") == null) {
            return "redirect:/user/login";
        }

        log.info("{}.register End!", this.getClass().getName());
        return "book/register";
    }

    /**
     * AI 맞춤 책 추천 화면을 반환한다.
     *
     * @param session 로그인 세션
     * @return book/recommend 템플릿
     */
    @GetMapping("/recommend")
    public String recommendPage(HttpSession session) {
        log.info("{}.recommendPage Start!", this.getClass().getName());

        if (session.getAttribute("SS_USER_ID") == null) {
            return "redirect:/user/login";
        }

        log.info("{}.recommendPage End!", this.getClass().getName());
        return "book/recommend";
    }

    /**
     * 유저의 독서 이력을 바탕으로 GPT 가 책 5권을 추천한다.
     * 추천 결과에 알라딘 API 로 표지 이미지를 추가하여 반환한다.
     *
     * @param session 로그인 세션
     * @return 추천 책 목록 JSON [{title, author, reason, cover}]
     */
    @GetMapping("/ai-recommend")
    @ResponseBody
    public ResponseEntity<?> getAiRecommend(HttpSession session) {
        log.info("{}.getAiRecommend Start!", this.getClass().getName());

        // 로그인 ID를 세션에서 직접 꺼낸다 (SS_USER_ID = users.user_id VARCHAR)
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        try {
            // 1. 유저가 등록한 책 목록 조회
            List<Book> userBooks = bookMapper.findBooksByUserId(userId);

            if (userBooks == null || userBooks.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            // 2. 책 목록을 "제목 - 저자" 형식의 문자열로 변환
            String bookList = userBooks.stream()
                    .map(b -> b.getTitle() + " - " + b.getAuthor())
                    .collect(Collectors.joining("\n"));

            // 3. GPT 추천 프롬프트 생성
            String prompt = """
                    사용자가 읽은 책 목록:
                    %s

                    위 책들을 바탕으로 이 사용자에게 어울리는 책 5권을 추천해줘.
                    반드시 아래 JSON 배열 형식으로만 반환하고 다른 말은 절대 하지 마.
                    [{"title": "책제목", "author": "저자", "reason": "추천이유 2~3문장"}]
                    """.formatted(bookList);

            // 4. GPT API 요청 본문 구성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("max_tokens", 1000);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            // 5. GPT API 호출
            String gptResponse = WebClient.create("https://api.openai.com")
                    .post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 6. GPT 응답에서 content 텍스트 추출
            JsonNode root = objectMapper.readTree(gptResponse);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            // 코드 블록 마커 제거 후 JSON 파싱
            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            List<Map<String, String>> result = objectMapper.readValue(
                    content,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            // 7. 각 추천 책의 표지 이미지를 알라딘 API 로 검색하여 추가
            for (Map<String, String> rec : result) {
                String cover = searchCoverFromAladin(rec.get("title"));
                rec.put("cover", cover);
            }

            log.info("{}.getAiRecommend End!", this.getClass().getName());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("AI 추천 오류", e);
            return ResponseEntity.status(500).body("추천 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * 알라딘 API 에서 책 제목으로 표지 이미지 URL 을 검색한다.
     *
     * @param title 검색할 책 제목
     * @return 표지 이미지 URL, 찾지 못하면 빈 문자열
     */
    private String searchCoverFromAladin(String title) {
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ttb/api/ItemSearch.aspx")
                            .queryParam("TTBKey",      apiKey)
                            .queryParam("Query",       title)
                            .queryParam("QueryType",   "Title")
                            .queryParam("MaxResults",  1)
                            .queryParam("SearchTarget","Book")
                            .queryParam("output",      "xml")
                            .queryParam("Version",     "20131101")
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

    /**
     * 알라딘 API 에서 책 제목으로 검색하고 결과를 반환한다.
     *
     * @param query 검색어
     * @return 검색된 책 목록 [{title, author, cover, isbn}]
     */
    @GetMapping("/search")
    @ResponseBody
    public List<Map<String, String>> searchBooks(@RequestParam String query) {
        log.info("{}.searchBooks Start! - query:{}", this.getClass().getName(), query);

        List<Map<String, String>> result = new ArrayList<>();
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ttb/api/ItemSearch.aspx")
                            .queryParam("TTBKey",      apiKey)
                            .queryParam("Query",       query)
                            .queryParam("QueryType",   "Title")
                            .queryParam("MaxResults",  10)
                            .queryParam("SearchTarget","Book")
                            .queryParam("output",      "xml")
                            .queryParam("Version",     "20131101")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");

            // XML item 요소마다 제목·저자·표지·ISBN 추출
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                Map<String, String> book = new HashMap<>();
                book.put("title",  getTagValue("title",  item));
                book.put("author", getTagValue("author", item));
                book.put("cover",  getTagValue("cover",  item));
                book.put("isbn",   getTagValue("isbn13", item));
                result.add(book);
            }
        } catch (Exception e) {
            log.error("알라딘 API 오류", e);
        }

        log.info("{}.searchBooks End! - 결과 {}건", this.getClass().getName(), result.size());
        return result;
    }

    /**
     * 선택한 책을 현재 유저의 독서 목록에 등록한다.
     * books 테이블에 책이 없으면 먼저 등록한 뒤 user_books 에 연결한다.
     *
     * @param title   책 제목
     * @param author  저자
     * @param cover   표지 이미지 URL (선택)
     * @param isbn    ISBN-13 (선택)
     * @param session 로그인 세션
     * @return {success, message}
     */
    @PostMapping("/register")
    @ResponseBody
    public Map<String, Object> registerBook(@RequestParam String title,
                                            @RequestParam String author,
                                            @RequestParam(required = false) String cover,
                                            @RequestParam(required = false) String isbn,
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
            // 이미 등록된 책인지 확인
            Book book = bookMapper.findByTitleAndAuthor(title, author);

            if (book == null) {
                // 새 책 등록
                book = new Book();
                book.setTitle(title);
                book.setAuthor(author);
                book.setCoverImage(cover);
                bookMapper.insertBook(book);
            }

            // 유저-책 연결 (중복이면 INSERT IGNORE 로 무시)
            bookMapper.insertUserBook(userId, book.getBookId());

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

    /**
     * 알라딘 베스트셀러 Top 10 을 반환한다.
     *
     * @return 베스트셀러 목록 [{title, author, cover, isbn}]
     */
    @GetMapping("/bestseller")
    @ResponseBody
    public List<Map<String, String>> getBestseller() {
        log.info("{}.getBestseller Start!", this.getClass().getName());

        List<Map<String, String>> result = new ArrayList<>();
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ttb/api/ItemList.aspx")
                            .queryParam("TTBKey",      apiKey)
                            .queryParam("QueryType",   "Bestseller")
                            .queryParam("MaxResults",  10)
                            .queryParam("SearchTarget","Book")
                            .queryParam("output",      "xml")
                            .queryParam("Version",     "20131101")
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
                book.put("title",  getTagValue("title",  item));
                book.put("author", getTagValue("author", item));
                book.put("cover",  getTagValue("cover",  item));
                book.put("isbn",   getTagValue("isbn13", item));
                result.add(book);
            }
        } catch (Exception e) {
            log.error("베스트셀러 API 오류", e);
        }

        log.info("{}.getBestseller End!", this.getClass().getName());
        return result;
    }

    /**
     * XML Element 에서 특정 태그의 텍스트 값을 추출한다.
     *
     * @param tag     추출할 태그명
     * @param element 부모 Element
     * @return 태그 텍스트 값, 없으면 빈 문자열
     */
    private String getTagValue(String tag, Element element) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() > 0 && list.item(0).getChildNodes().getLength() > 0) {
            return list.item(0).getChildNodes().item(0).getNodeValue();
        }
        return "";
    }
}
