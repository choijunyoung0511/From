package com.from.controller;

import com.from.repository.entity.BookEntity;
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

@Slf4j
@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final IBookService bookService;
    private final IReviewService reviewService;

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
            IReviewService.ReviewResult review = reviewService.generateAndSave(
                    userId, bookId, emphasis, tone,
                    LocalDate.parse(deliveryDate),
                    LocalTime.parse(deliveryTime),
                    paperId
            );

            session.setAttribute("reviewContent",      review.content());
            session.setAttribute("reviewBookTitle",    review.bookTitle());
            session.setAttribute("reviewBookAuthor",   review.bookAuthor());
            session.setAttribute("reviewPaperId",      paperId);
            session.setAttribute("reviewDeliveryDate", deliveryDate);
            session.setAttribute("reviewDeliveryTime", deliveryTime);

            result.put("success",  true);
            result.put("reviewId", "result");

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("독후감 생성 오류", e);
            result.put("success", false);
            result.put("message", "독후감 생성 중 오류가 발생했습니다.");
        }

        log.info("{}.generateReview End!", this.getClass().getName());
        return result;
    }

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

    @GetMapping("/books")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getUserBooks(HttpSession session) {
        log.info("{}.getUserBooks Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).build();

        List<Map<String, Object>> result = new ArrayList<>();
        for (BookEntity book : bookService.findByUserId(userId)) {
            Map<String, Object> bookData = new HashMap<>();
            bookData.put("title",  book.getTitle());
            bookData.put("author", book.getAuthor());
            bookData.put("emoji",  getEmojiForBook(book.getTitle()));
            result.add(bookData);
        }

        log.info("{}.getUserBooks End!", this.getClass().getName());
        return ResponseEntity.ok(result);
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