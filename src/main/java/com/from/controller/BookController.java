package com.from.controller;

import com.from.domain.Book;
import com.from.dto.user.SessionUser;
import com.from.mapper.BookMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    @Value("${aladin.api.key}")
    private String apiKey;

    private final BookMapper bookMapper;  // ← 추가

    @GetMapping("/register")
    public String register(HttpSession session) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";
        return "book/register";
    }

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
            // 이미 등록된 책인지 확인
            Book book = bookMapper.findByTitleAndAuthor(title, author);

            // 없으면 새로 등록
            if (book == null) {
                book = new Book();
                book.setTitle(title);
                book.setAuthor(author);
                book.setCoverImage(cover);
                bookMapper.insertBook(book);
            }

            // user_books 연결
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

    private String getTagValue(String tag, Element element) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() > 0 && list.item(0).getChildNodes().getLength() > 0) {
            return list.item(0).getChildNodes().item(0).getNodeValue();
        }
        return "";
    }
}