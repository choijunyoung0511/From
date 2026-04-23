package com.from.controller;

import com.from.dto.MsgDTO;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 책 검색·등록·AI 추천을 처리하는 컨트롤러.
 * /book/** 경로는 LoginInterceptor가 인증을 검사한다.
 */
@Slf4j
@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final IBookService bookService;
    private final IAladinService aladinService;
    private final IReviewService reviewService;

    /**
     * 책 등록 화면을 반환한다.
     * 알라딘 API로 책을 검색하고 내 서재에 등록하는 페이지이다.
     */
    @GetMapping("/register")
    public String register(HttpSession session) {
        log.info("{}.register Start!", this.getClass().getName());
        if (session.getAttribute("SS_USER_ID") == null) return "redirect:/user/login";
        log.info("{}.register End!", this.getClass().getName());
        return "book/register";
    }

    /**
     * AI 책 추천 화면을 반환한다.
     * 유저의 독서 이력을 GPT에 전달하여 5권을 추천받는 페이지이다.
     */
    @GetMapping("/recommend")
    public String recommendPage(HttpSession session) {
        log.info("{}.recommendPage Start!", this.getClass().getName());
        if (session.getAttribute("SS_USER_ID") == null) return "redirect:/user/login";
        log.info("{}.recommendPage End!", this.getClass().getName());
        return "book/recommend";
    }

    /**
     * GPT 기반 AI 책 추천 결과를 JSON으로 반환한다. (비동기 AJAX)
     * 유저의 독서 이력을 GPT에 전달하여 [{title, author, reason, cover}] 형태로 반환한다.
     *
     * @return 추천 책 목록 또는 오류 MsgDTO
     */
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

    /**
     * 알라딘 API로 책을 검색하여 결과를 JSON으로 반환한다. (비동기 AJAX)
     *
     * @param query 검색어
     * @param type  검색 유형 (Keyword, Title, Author)
     * @return [{title, author, cover, isbn}] 형태의 검색 결과 목록
     */
    @GetMapping("/search")
    @ResponseBody
    public List<Map<String, String>> searchBooks(@RequestParam String query,
                                                  @RequestParam(defaultValue = "Keyword") String type) {
        log.info("{}.searchBooks Start! - query:{}, type:{}", this.getClass().getName(), query, type);
        List<Map<String, String>> result = aladinService.searchBooks(query, type);
        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    /**
     * 알라딘 베스트셀러 Top 10을 JSON으로 반환한다. (비동기 AJAX)
     * 책 등록 화면에서 "베스트셀러" 탭을 눌렀을 때 호출된다.
     *
     * @return [{title, author, cover, isbn}] 형태의 베스트셀러 목록
     */
    @GetMapping("/bestseller")
    @ResponseBody
    public List<Map<String, String>> getBestseller() {
        log.info("{}.getBestseller Start!", this.getClass().getName());
        List<Map<String, String>> result = aladinService.getBestseller();
        log.info("{}.getBestseller End!", this.getClass().getName());
        return result;
    }

    /**
     * 책을 내 서재에 등록한다. (비동기 AJAX, POST)
     * DB에 같은 제목+저자의 책이 없으면 새로 저장하고, 있으면 기존 책에 연결한다.
     * 이미 등록된 책이면 MsgDTO(result=0)를 반환한다.
     *
     * @param title  책 제목
     * @param author 저자명
     * @param cover  표지 이미지 URL (선택)
     * @return {result:1, msg:"등록되었습니다."} 또는 오류 MsgDTO
     */
    @PostMapping("/register")
    @ResponseBody
    public MsgDTO registerBook(@RequestParam String title,
                               @RequestParam String author,
                               @RequestParam(required = false) String cover,
                               HttpSession session) {
        log.info("{}.registerBook Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build();

        MsgDTO dto;
        try {
            // DB에 같은 책이 있으면 재사용, 없으면 새로 저장
            Optional<BookEntity> existing = bookService.findByTitleAndAuthor(title, author);
            BookEntity book = existing.orElseGet(() ->
                    bookService.save(BookEntity.builder()
                            .title(title)
                            .author(author)
                            .coverImage(cover)
                            .build())
            );

            // 유저-책 연결 저장 (이미 등록된 경우 false 반환)
            boolean registered = bookService.saveUserBook(userId, book.getBookId());
            dto = registered
                    ? MsgDTO.builder().result(1).msg("등록되었습니다.").build()
                    : MsgDTO.builder().result(0).msg("이미 등록된 책입니다.").build();

        } catch (Exception e) {
            log.error("책 등록 오류", e);
            dto = MsgDTO.builder().result(0).msg("등록 중 오류가 발생했습니다.").build();
        }

        log.info("{}.registerBook End!", this.getClass().getName());
        return dto;
    }
}