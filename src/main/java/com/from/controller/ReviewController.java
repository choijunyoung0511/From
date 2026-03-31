package com.from.controller;

import com.from.domain.Book;
import com.from.dto.user.SessionUser;
import com.from.mapper.BookMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final BookMapper bookMapper;

    // AI 독후감 생성 (기존)
    @GetMapping("/create")
    public String create() {
        return "review/create";
    }

    // AI 독후감 결과 (기존)
    @GetMapping("/result")
    public String result() {
        return "review/result";
    }

    // 책 속 주인공 이미지 생성 (신규)
    @GetMapping("/image")
    public String image() {
        return "review/image";
    }

    // 책 추천 (신규)
    @GetMapping("/recommend")
    public String recommend() {
        return "review/recommend";
    }

    // 등록된 책 목록 조회
    @GetMapping("/books")
    @ResponseBody
    public List<Map<String, Object>> getUserBooks(HttpSession session) {
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        if (loginUser == null) {
            return new ArrayList<>();
        }

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

    // 이미지 생성 요청
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
            // TODO: 실제 AI 이미지 생성 로직 구현
            // 현재는 임시로 성공 응답만 반환
            result.put("success", true);
            result.put("imageUrl", "");
            result.put("description", getDescriptionForBook(bookTitle));

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "이미지 생성 중 오류가 발생했습니다.");
        }

        return result;
    }

    // 이미지 생성 히스토리 조회
    @GetMapping("/image/history")
    @ResponseBody
    public List<Map<String, Object>> getImageHistory(HttpSession session) {
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        if (loginUser == null) {
            return new ArrayList<>();
        }

        // TODO: 실제 히스토리 DB 조회 구현
        // 현재는 임시 데이터 반환
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
