package com.from.controller;

import com.from.dto.BookSearchDTO;
import com.from.dto.MsgDTO;
import com.from.service.IBookService;
import com.from.service.IReviewService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 독후감 생성 흐름을 처리하는 컨트롤러.
 * 4단계 폼(책 선택→강조 내용→날짜/시간→편지지)을 통해 GPT 독후감을 생성하고
 * 결과를 세션에 담아 결과 페이지로 전달한다.
 * /review/** 경로는 LoginInterceptor가 인증을 검사한다.
 */
@Slf4j
@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final IBookService bookService;
    private final IReviewService reviewService;

    /**
     * 독후감 작성 화면을 반환한다.
     * 유저가 등록한 책 목록을 Model에 담아 책 선택 드롭다운에 사용한다.
     */
    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        log.info("{}.create Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId != null) {
            // 책 선택 드롭다운에 표시할 유저의 등록 책 목록
            model.addAttribute("myBooks", bookService.findByUserId(userId));
        }

        log.info("{}.create End!", this.getClass().getName());
        return "review/create";
    }

    /**
     * GPT로 독후감을 생성하고 세션에 저장한다. (비동기 AJAX, POST)
     * 생성 완료 후 클라이언트는 /review/result 페이지로 이동한다.
     *
     * @param bookId       선택한 책의 ID
     * @param emphasis     강조할 내용 (사용자 입력)
     * @param tone         작성 톤 (FORMAL / CASUAL / EMOTIONAL / ANALYTICAL)
     * @param deliveryDate 미래 이메일 발송 날짜 (yyyy-MM-dd)
     * @param deliveryTime 미래 이메일 발송 시각 (HH:mm)
     * @param paperId      편지지 테마 번호 (1~12)
     * @return {result:1} 성공 또는 오류 MsgDTO
     */
    @PostMapping("/generate")
    @ResponseBody
    public MsgDTO generateReview(
            @RequestParam Long bookId,
            @RequestParam String emphasis,
            @RequestParam String tone,
            @RequestParam String deliveryDate,
            @RequestParam String deliveryTime,
            @RequestParam int paperId,
            HttpSession session) {

        log.info("{}.generateReview Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build();

        MsgDTO dto;
        try {
            // GPT API 호출 + MongoDB 저장
            IReviewService.ReviewResult review = reviewService.generateAndSave(
                    userId, bookId, emphasis, tone,
                    LocalDate.parse(deliveryDate),
                    LocalTime.parse(deliveryTime),
                    paperId
            );

            // 결과 화면(review/result)에서 꺼내 쓸 수 있도록 세션에 저장
            // PRG(Post-Redirect-Get) 패턴을 쓰지 않고 세션을 활용
            session.setAttribute("reviewContent",      review.content());
            session.setAttribute("reviewBookTitle",    review.bookTitle());
            session.setAttribute("reviewBookAuthor",   review.bookAuthor());
            session.setAttribute("reviewPaperId",      paperId);
            session.setAttribute("reviewDeliveryDate", deliveryDate);
            session.setAttribute("reviewDeliveryTime", deliveryTime);

            dto = MsgDTO.builder().result(1).msg("독후감이 생성되었습니다.").build();

        } catch (IllegalArgumentException e) {
            dto = MsgDTO.builder().result(0).msg(e.getMessage()).build();
        } catch (Exception e) {
            log.error("독후감 생성 오류", e);
            dto = MsgDTO.builder().result(0).msg("독후감 생성 중 오류가 발생했습니다.").build();
        }

        log.info("{}.generateReview End!", this.getClass().getName());
        return dto;
    }

    /**
     * 독후감 결과 화면을 반환한다.
     * generateReview()가 세션에 저장한 독후감 내용과 책 정보를 Model에 담아 전달한다.
     */
    @GetMapping("/result")
    public String result(HttpSession session, Model model) {
        log.info("{}.result Start!", this.getClass().getName());
        // 세션에서 독후감 결과 데이터를 꺼내 Thymeleaf 템플릿에 전달
        model.addAttribute("reviewContent",      session.getAttribute("reviewContent"));
        model.addAttribute("reviewBookTitle",    session.getAttribute("reviewBookTitle"));
        model.addAttribute("reviewBookAuthor",   session.getAttribute("reviewBookAuthor"));
        model.addAttribute("reviewPaperId",      session.getAttribute("reviewPaperId"));
        model.addAttribute("reviewDeliveryDate", session.getAttribute("reviewDeliveryDate"));
        model.addAttribute("reviewDeliveryTime", session.getAttribute("reviewDeliveryTime"));
        log.info("{}.result End!", this.getClass().getName());
        return "review/result";
    }

    /**
     * 독후감 이미지 생성 화면을 반환한다. (SPA 방식)
     * review/image.html에서 3단계 이미지 생성 플로우를 클라이언트 사이드로 처리한다.
     */
    @GetMapping("/image")
    public String image() {
        return "review/image";
    }

    /** AI 책 추천 화면을 반환한다. */
    @GetMapping("/recommend")
    public String recommend() {
        return "review/recommend";
    }

    /**
     * 유저가 등록한 책 목록을 JSON으로 반환한다. (비동기 AJAX)
     * 독후감 작성 화면의 책 선택 드롭다운에 사용된다.
     * 책 제목에 따라 이모지를 자동으로 매핑하여 UI를 풍성하게 한다.
     *
     * @return [{title, author, emoji}] 형태의 책 목록
     */
    @GetMapping("/books")
    @ResponseBody
    public ResponseEntity<?> getUserBooks(HttpSession session) {
        log.info("{}.getUserBooks Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body
                (MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build());

        List<Map<String, Object>> result = new ArrayList<>();
        for (BookSearchDTO book : bookService.findByUserId(userId)) {
            Map<String, Object> bookData = new HashMap<>();
            bookData.put("title",  book.title());
            bookData.put("author", book.author());
            // 책 제목에 따라 이모지를 매핑 (일치하지 않으면 기본 📚)
            bookData.put("emoji",  getEmojiForBook(book.title()));
            result.add(bookData);
        }

        log.info("{}.getUserBooks End!", this.getClass().getName());
        return ResponseEntity.ok(result);
    }

    /**
     * 책 제목 키워드에 따라 대표 이모지를 반환한다.
     * 일치하는 키워드가 없으면 기본 이모지(📚)를 반환한다.
     */
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