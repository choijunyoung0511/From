// ReviewController.java - AI 독후감 생성 흐름을 처리하는 컨트롤러
// 4단계 폼(책 선택 → 강조 내용 → 날짜/시간 → 편지지)을 통해 GPT 독후감을 생성하고
// 결과를 세션에 담아 결과 페이지로 전달한다
// /review/** 경로는 LoginInterceptor 가 인증을 검사한다
package com.from.controller;

import com.from.dto.BookSearchDto;  // 도서 검색 결과 DTO
import com.from.dto.MsgDto;         // 공통 응답 메시지 DTO (result + msg)
import com.from.dto.ReviewBookDto;  // 독후감 작성용 책 목록 DTO (title, author, emoji)
import com.from.service.IBookService;   // 도서 비즈니스 로직 서비스 인터페이스
import com.from.service.IReviewService; // 독후감 생성 서비스 인터페이스
import jakarta.servlet.http.HttpSession; // HTTP 세션 (로그인 사용자 확인용)
import lombok.RequiredArgsConstructor;   // final 필드 기반 생성자 자동 생성
import lombok.extern.slf4j.Slf4j;        // SLF4J 로거 자동 생성
import org.springframework.http.ResponseEntity; // HTTP 상태코드 포함 응답 객체
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;            // 템플릿에 데이터 전달용
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;     // 날짜 처리
import java.time.LocalDateTime; // 날짜+시간 처리
import java.time.LocalTime;     // 시간 처리
import java.util.List;

@Slf4j                      // log.info(), log.error() 등 로거 사용 가능하게 해주는 어노테이션
@Controller                 // Spring MVC 컨트롤러로 등록 (View 이름 반환 가능)
@RequestMapping("/review")  // 이 컨트롤러의 모든 URL은 /review 로 시작
@RequiredArgsConstructor    // final 필드를 인자로 받는 생성자 자동 생성 (의존성 주입)
public class ReviewController {

    private final IBookService bookService;     // 도서 DB 처리 서비스
    private final IReviewService reviewService; // GPT 독후감 생성 및 저장 서비스

    // [GET] /review/create - 독후감 작성 화면 반환
    // 유저가 등록한 책 목록을 Model 에 담아 책 선택 드롭다운에 사용
    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        log.info("{}.create Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID"); // 세션에서 로그인 사용자 ID 추출
        if (userId != null) {
            model.addAttribute("myBooks", bookService.findByUserId(userId)); // 책 선택 드롭다운에 표시할 유저의 등록 책 목록 전달
        }

        log.info("{}.create End!", this.getClass().getName());
        return "review/create"; // templates/review/create.html 반환
    }

    // [POST] /review/generate - GPT 로 독후감을 생성하고 세션에 저장 (비동기 AJAX)
    // 생성 완료 후 클라이언트는 /review/result 페이지로 이동
    @PostMapping("/generate")
    @ResponseBody // 반환값을 JSON 으로 직렬화하여 응답 body 에 담음
    public MsgDto generateReview(
            @RequestParam Long bookId,          // 선택한 책의 ID
            @RequestParam String emphasis,      // 강조할 내용 (사용자 입력)
            @RequestParam String tone,          // 작성 톤 (FORMAL / CASUAL / EMOTIONAL / ANALYTICAL)
            @RequestParam String deliveryDate,  // 미래 이메일 발송 날짜 (yyyy-MM-dd)
            @RequestParam String deliveryTime,  // 미래 이메일 발송 시각 (HH:mm)
            @RequestParam int paperId,          // 편지지 테마 번호 (1~12)
            HttpSession session) {

        log.info("{}.generateReview Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDto.builder().result(0).msg("로그인이 필요합니다.").build(); // 비로그인 시 에러 반환

        LocalDate parsedDate = LocalDate.parse(deliveryDate); // 문자열 발송 날짜를 LocalDate 로 변환
        LocalTime parsedTime = LocalTime.parse(deliveryTime); // 문자열 발송 시각을 LocalTime 으로 변환
        if (LocalDateTime.of(parsedDate, parsedTime).isBefore(LocalDateTime.now())) { // 발송 날짜+시간이 현재보다 과거이면 에러
            return MsgDto.builder().result(0).msg("발송 날짜와 시간은 현재 이후여야 합니다.").build();
        }

        MsgDto dto;
        try {
            // GPT API 호출 후 MongoDB 에 독후감 저장
            IReviewService.ReviewResult review = reviewService.generateAndSave(
                    userId, bookId, emphasis, tone,
                    LocalDate.parse(deliveryDate),
                    LocalTime.parse(deliveryTime),
                    paperId
            );

            // 결과 화면(review/result)에서 꺼내 쓸 수 있도록 세션에 저장
            // PRG(Post-Redirect-Get) 패턴을 쓰지 않고 세션을 활용
            session.setAttribute("reviewContent",      review.content());     // 생성된 독후감 본문
            session.setAttribute("reviewBookTitle",    review.bookTitle());   // 책 제목
            session.setAttribute("reviewBookAuthor",   review.bookAuthor());  // 저자
            session.setAttribute("reviewPaperId",      paperId);              // 편지지 테마 번호
            session.setAttribute("reviewDeliveryDate", deliveryDate);         // 이메일 발송 날짜
            session.setAttribute("reviewDeliveryTime", deliveryTime);         // 이메일 발송 시각

            dto = MsgDto.builder().result(1).msg("독후감이 생성되었습니다.").build();

        } catch (IllegalArgumentException e) { // 유효하지 않은 입력값 예외 처리
            dto = MsgDto.builder().result(0).msg(e.getMessage()).build();
        } catch (Exception e) { // GPT 호출 실패 등 예외 처리
            log.error("독후감 생성 오류", e);
            dto = MsgDto.builder().result(0).msg("독후감 생성 중 오류가 발생했습니다.").build();
        }

        log.info("{}.generateReview End!", this.getClass().getName());
        return dto;
    }

    // [GET] /review/result - 독후감 결과 화면 반환
    // generateReview() 가 세션에 저장한 독후감 내용과 책 정보를 Model 에 담아 전달
    @GetMapping("/result")
    public String result(HttpSession session, Model model) {
        log.info("{}.result Start!", this.getClass().getName());

        // 세션에서 독후감 결과 데이터를 꺼내 Thymeleaf 템플릿에 전달
        model.addAttribute("reviewContent",      session.getAttribute("reviewContent"));      // 생성된 독후감 본문
        model.addAttribute("reviewBookTitle",    session.getAttribute("reviewBookTitle"));    // 책 제목
        model.addAttribute("reviewBookAuthor",   session.getAttribute("reviewBookAuthor"));   // 저자
        model.addAttribute("reviewPaperId",      session.getAttribute("reviewPaperId"));      // 편지지 테마 번호
        model.addAttribute("reviewDeliveryDate", session.getAttribute("reviewDeliveryDate")); // 이메일 발송 날짜
        model.addAttribute("reviewDeliveryTime", session.getAttribute("reviewDeliveryTime")); // 이메일 발송 시각

        log.info("{}.result End!", this.getClass().getName());
        return "review/result"; // templates/review/result.html 반환
    }

    // [GET] /review/image - 독후감 이미지 생성 화면 반환 (SPA 방식)
    // review/image.html 에서 3단계 이미지 생성 플로우를 클라이언트 사이드로 처리
    @GetMapping("/image")
    public String image() {
        return "review/image"; // templates/review/image.html 반환
    }

    // [GET] /review/recommend - AI 책 추천 화면 반환
    @GetMapping("/recommend")
    public String recommend() {
        return "review/recommend"; // templates/review/recommend.html 반환
    }

    // [GET] /review/books - 유저가 등록한 책 목록을 JSON 으로 반환 (비동기 AJAX)
    // 독후감 작성 화면의 책 선택 드롭다운에 사용
    // 책 제목에 따라 이모지를 자동으로 매핑하여 UI 를 풍성하게 함
    // 반환: [{title, author, emoji}] 형태의 책 목록
    @GetMapping("/books")
    @ResponseBody
    public ResponseEntity<?> getUserBooks(HttpSession session) {
        log.info("{}.getUserBooks Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(
                MsgDto.builder().result(0).msg("로그인이 필요합니다.").build()); // 비로그인 시 401 반환

        List<ReviewBookDto> result = bookService.findByUserId(userId).stream()
                .map(book -> new ReviewBookDto(
                        book.title(),                    // 책 제목
                        book.author(),                   // 저자
                        getEmojiForBook(book.title())    // 책 제목 키워드 기반 이모지 매핑
                ))
                .toList();

        log.info("{}.getUserBooks End!", this.getClass().getName());
        return ResponseEntity.ok(result); // 책 목록 JSON 반환
    }

    // [유틸] getEmojiForBook - 책 제목 키워드에 따라 대표 이모지 반환
    // 일치하는 키워드가 없으면 기본 이모지(📚) 반환
    private String getEmojiForBook(String title) {
        if (title.contains("사랑"))    return "📖"; // 사랑 관련 책
        if (title.contains("채식"))    return "🌿"; // 채식 관련 책
        if (title.contains("코스모스")) return "🌌"; // 우주/과학 관련 책
        if (title.contains("데미안"))  return "🦋"; // 데미안
        if (title.contains("1984"))    return "👁";  // 1984
        if (title.contains("사피엔스")) return "🦴"; // 사피엔스
        return "📚"; // 키워드 없으면 기본 이모지
    }
}