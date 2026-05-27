// BookController.java - 도서 관련 HTTP 요청을 처리하는 컨트롤러
// 담당 기능: 도서 등록, 검색, 추천, 별점/후기, 댓글, 좋아요
package com.from.controller;

import com.from.dto.BookRatingDto;       // 별점/후기 데이터 DTO
import com.from.dto.BookRecommendDto;    // 추천 도서 데이터 DTO
import com.from.dto.BookSearchDto;       // 도서 검색 결과 데이터 DTO
import com.from.dto.MsgDto;              // 공통 응답 메시지 DTO (result + msg)
import com.from.dto.RecommendSectionDto; // 섹션형 추천 데이터 DTO (type, label, books)

import com.from.service.IAladinService;  // 알라딘 API 연동 서비스 인터페이스
import com.from.service.IBookService;    // 도서 비즈니스 로직 서비스 인터페이스

import com.from.service.impl.RankingRedisService; // Redis 기반 독서량 랭킹 서비스

import jakarta.servlet.http.HttpSession; // HTTP 세션 (로그인 사용자 확인용)
import lombok.RequiredArgsConstructor;   // final 필드 기반 생성자 자동 생성
import lombok.extern.slf4j.Slf4j;        // SLF4J 로거 자동 생성

import org.springframework.http.ResponseEntity;   // HTTP 상태코드 포함 응답 객체
import org.springframework.stereotype.Controller; // Spring MVC 컨트롤러로 등록
import org.springframework.web.bind.annotation.*; // @GetMapping 등 HTTP 매핑 어노테이션

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture; // 비동기 병렬 처리
import java.util.concurrent.TimeUnit;          // 타임아웃 단위 설정
import java.util.stream.Collectors;

@Slf4j                       // log.info(), log.error() 등 로거 사용 가능하게 해주는 어노테이션
@Controller                  // Spring MVC 컨트롤러로 등록 (View 이름 반환 가능)
@RequestMapping("/book")     // 이 컨트롤러의 모든 URL은 /book 으로 시작
@RequiredArgsConstructor     // final 필드를 인자로 받는 생성자 자동 생성 (의존성 주입)
public class BookController {

    private final IBookService bookService;                // 도서 DB 처리 서비스
    private final IAladinService aladinService;            // 알라딘 외부 API 서비스
    private final RankingRedisService rankingRedisService; // Redis 독서량 랭킹 서비스

    // [GET] /book/register - 도서 등록 페이지 이동
    // 비로그인 사용자는 로그인 페이지로 리다이렉트
    @GetMapping("/register")
    public String register(HttpSession session) {
        log.info("{}.register Start!", this.getClass().getName()); // 메서드 시작 로그
        if (session.getAttribute("SS_USER_ID") == null) return "redirect:/user/login"; // 비로그인 시 로그인 페이지로 이동
        log.info("{}.register End!", this.getClass().getName()); // 메서드 종료 로그
        return "book/register"; // templates/book/register.html 반환
    }

    // [GET] /book/recommend - 도서 추천 페이지 이동
    // 비로그인 사용자는 로그인 페이지로 리다이렉트
    @GetMapping("/recommend")
    public String recommendPage(HttpSession session) {
        log.info("{}.recommendPage Start!", this.getClass().getName());
        if (session.getAttribute("SS_USER_ID") == null) return "redirect:/user/login"; // 비로그인 시 로그인 페이지로 이동
        log.info("{}.recommendPage End!", this.getClass().getName());
        return "book/recommend"; // templates/book/recommend.html 반환
    }

    /**
     * [GET] /book/section-recommend - 섹션형 맞춤 추천 (JSON 반환)
     * 읽은 책의 작가·카테고리를 분석 후 알라딘 API를 병렬 호출하여 섹션 목록을 반환
     * 반환: List<RecommendSectionDto>
     */
    @GetMapping("/section-recommend")
    @ResponseBody // 반환값을 JSON으로 직렬화하여 응답 body에 담음
    public ResponseEntity<?> getSectionRecommend(HttpSession session) {
        log.info("{}.getSectionRecommend Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID"); // 세션에서 로그인 사용자 ID 추출
        if (userId == null) {
            return ResponseEntity.status(401).body(List.of()); // 비로그인 시 401 반환
        }

        List<BookSearchDto> readBooks = bookService.findByUserId(userId); // 사용자가 등록한 도서 목록 조회
        if (readBooks.isEmpty()) {
            return ResponseEntity.ok(List.of()); // 등록한 책이 없으면 빈 배열 반환
        }

        // 이미 읽은 책 제목 집합 생성 (소문자 + 공백 제거 → 중복 제거용)
        Set<String> readTitles = readBooks.stream()
                .map(b -> b.title().toLowerCase().trim()) // 대소문자 무시, 앞뒤 공백 제거
                .collect(Collectors.toSet());             // Set으로 수집 (중복 자동 제거)

        // 최근 등록 순으로 정렬 후 작가 상위 2명 추출
        List<String> topAuthors = readBooks.stream()
                .sorted(Comparator.comparing(BookSearchDto::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))) // 등록일 내림차순 (null은 맨 뒤)
                .map(b -> cleanAuthor(b.author()))  // "(지은이)" 등 괄호 제거 → 순수 이름만 추출
                .filter(a -> !a.isBlank())          // 빈 문자열 제외
                .distinct()                         // 중복 작가 제거
                .limit(2)                           // 상위 2명만 선택
                .toList();

        // 카테고리 빈도 분석 후 상위 2개 추출
        List<String> topCategories = readBooks.stream()
                .map(b -> extractLeafCategory(b.category())) // 말단 카테고리만 추출 (예: "한국소설")
                .filter(c -> !c.isBlank())                   // 빈 카테고리 제외
                .collect(Collectors.groupingBy(c -> c, Collectors.counting())) // 카테고리별 카운트
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()) // 많이 읽은 카테고리 우선 정렬
                .limit(2)                                    // 상위 2개 선택
                .map(Map.Entry::getKey)
                .toList();

        List<CompletableFuture<RecommendSectionDto>> futures = new ArrayList<>(); // 비동기 작업 목록

        // 작가 기반 섹션: 작가명으로 알라딘 검색 후 이미 읽은 책 제외
        for (String author : topAuthors) {
            futures.add(CompletableFuture.supplyAsync(() -> { // 비동기로 실행
                List<BookSearchDto> results = aladinService.searchBooks(author, "Author"); // 작가명으로 알라딘 API 검색
                List<BookSearchDto> filtered = results.stream()
                        .filter(b -> !readTitles.contains(b.title().toLowerCase().trim())) // 이미 읽은 책 제외
                        .limit(6)  // 섹션당 최대 6권
                        .toList();
                return RecommendSectionDto.builder()
                        .type("author")                    // 섹션 타입: 작가
                        .label(author + " 작가의 다른 책") // 화면에 표시될 섹션 제목
                        .icon("✍️")                       // 섹션 아이콘
                        .books(filtered)                   // 필터링된 도서 목록
                        .build();
            }));
        }

        // 카테고리 기반 섹션: 카테고리 키워드로 알라딘 검색 후 이미 읽은 책 제외
        for (String category : topCategories) {
            futures.add(CompletableFuture.supplyAsync(() -> { // 비동기로 실행
                List<BookSearchDto> results = aladinService.searchBooks(category, "Keyword"); // 키워드로 알라딘 API 검색
                List<BookSearchDto> filtered = results.stream()
                        .filter(b -> !readTitles.contains(b.title().toLowerCase().trim())) // 이미 읽은 책 제외
                        .limit(6) // 섹션당 최대 6권
                        .toList();
                return RecommendSectionDto.builder()
                        .type("category")           // 섹션 타입: 카테고리
                        .label(category + " 추천")  // 섹션 제목
                        .icon("📚")                 // 섹션 아이콘
                        .books(filtered)
                        .build();
            }));
        }

        // 모든 비동기 작업 결과 수집 (최대 30초 대기)
        List<RecommendSectionDto> sections = futures.stream()
                .map(f -> {
                    try {
                        return f.get(30, TimeUnit.SECONDS); // 30초 초과 시 예외 발생
                    } catch (Exception e) {
                        log.error("섹션 추천 조회 오류", e); // 실패 시 로그만 남기고 null 반환
                        return null;
                    }
                })
                .filter(s -> s != null && !s.books().isEmpty()) // null이거나 책이 없는 섹션 제거
                .toList();

        log.info("{}.getSectionRecommend End! - {}섹션", this.getClass().getName(), sections.size());
        return ResponseEntity.ok(sections); // 완성된 섹션 목록 JSON으로 반환
    }

    /**
     * [GET] /book/ai-recommend - 카테고리 기반 맞춤 추천 (JSON 반환)
     * GPT 미사용, 알라딘 키워드 검색만 사용 → 비용 절감 + 빠른 응답
     * 반환: List<BookRecommendDto> (최대 5권)
     */
    @GetMapping("/ai-recommend")
    @ResponseBody
    public ResponseEntity<?> getAiRecommend(HttpSession session) {
        log.info("{}.getAiRecommend Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID"); // 세션에서 사용자 ID 추출
        if (userId == null)
            return ResponseEntity.status(401).body(
                    MsgDto.builder().result(0).msg("로그인이 필요합니다.").build() // 비로그인 시 에러 메시지 반환
            );

        List<BookSearchDto> readBooks = bookService.findByUserId(userId); // 사용자 등록 도서 목록 조회
        if (readBooks.isEmpty()) return ResponseEntity.ok(List.of()); // 등록 도서 없으면 빈 배열 반환

        // 이미 읽은 책 제목 집합 (중복 추천 방지용)
        Set<String> readTitles = readBooks.stream()
                .map(b -> b.title().toLowerCase().trim())
                .collect(Collectors.toSet());

        // 카테고리 빈도 상위 3개 추출 (추천 키워드로 활용)
        List<String> topCategories = readBooks.stream()
                .map(b -> extractLeafCategory(b.category())) // 말단 카테고리 추출
                .filter(c -> !c.isBlank())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting())) // 카테고리별 빈도 집계
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()) // 빈도 내림차순 정렬
                .limit(3) // 상위 3개 선택
                .map(Map.Entry::getKey)
                .toList();

        if (topCategories.isEmpty()) topCategories = List.of("소설", "자기계발", "에세이"); // 카테고리 없으면 기본값 사용

        // 카테고리별 알라딘 검색 후 읽지 않은 책 최대 5권 수집
        List<BookRecommendDto> result = new ArrayList<>();
        for (String category : topCategories) {
            if (result.size() >= 5) break; // 5권 채우면 종료

            List<BookSearchDto> found = aladinService.searchBooks(category, "Keyword"); // 키워드로 알라딘 검색

            for (BookSearchDto b : found) {
                if (result.size() >= 5) break; // 5권 채우면 내부 루프도 종료
                if (!readTitles.contains(b.title().toLowerCase().trim())) { // 이미 읽은 책이 아닌 경우만 추가
                    result.add(new BookRecommendDto(
                            b.title(),                                    // 책 제목
                            b.author(),                                   // 저자
                            b.cover() != null ? b.cover() : "",           // 표지 이미지 URL (없으면 빈 문자열)
                            "'" + category + "' 카테고리 기반 추천"        // 추천 이유 문구
                    ));
                    readTitles.add(b.title().toLowerCase().trim()); // 방금 추가한 책도 중복 방지 집합에 추가
                }
            }
        }

        log.info("{}.getAiRecommend End! - {}권", this.getClass().getName(), result.size());
        return ResponseEntity.ok(result); // 추천 도서 목록 JSON으로 반환
    }

    // [GET] /book/search?query=...&type=... - 알라딘 도서 검색 (JSON 반환)
    // query: 검색어, type: Keyword(기본값) / Author / Title
    @GetMapping("/search")
    @ResponseBody
    public List<BookSearchDto> searchBooks(
            @RequestParam String query,                             // 검색어 (필수)
            @RequestParam(defaultValue = "Keyword") String type) { // 검색 타입 (기본: 키워드)
        log.info("{}.searchBooks Start! - query:{}, type:{}", this.getClass().getName(), query, type);
        List<BookSearchDto> result = aladinService.searchBooks(query, type); // 알라딘 API 호출
        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    // [GET] /book/bestseller - 알라딘 베스트셀러 목록 조회 (JSON 반환)
    @GetMapping("/bestseller")
    @ResponseBody
    public List<BookSearchDto> getBestseller() {
        log.info("{}.getBestseller Start!", this.getClass().getName());
        List<BookSearchDto> result = aladinService.getBestseller(); // 알라딘 베스트셀러 API 호출
        log.info("{}.getBestseller End!", this.getClass().getName());
        return result;
    }

    // [POST] /book/register - 도서를 사용자 독서 목록에 등록
    // 이미 DB에 있는 책이면 재사용, 없으면 새로 저장
    // 성공 시 Redis 독서량 카운터 증가
    @PostMapping("/register")
    @ResponseBody
    public MsgDto registerBook(
            @RequestParam String title,                                            // 책 제목
            @RequestParam String author,                                           // 저자
            @RequestParam(required = false, defaultValue = "") String cover,       // 표지 이미지 URL
            @RequestParam(required = false, defaultValue = "") String description, // 책 소개
            @RequestParam(required = false, defaultValue = "") String category,    // 카테고리
            HttpSession session) {

        log.info("{}.registerBook Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID"); // 세션에서 사용자 ID 추출
        if (userId == null) return MsgDto.builder().result(0).msg("로그인이 필요합니다.").build(); // 비로그인 시 에러 반환

        MsgDto dto;
        try {
            Optional<BookSearchDto> existing = bookService.findByTitleAndAuthor(title, author); // 동일한 제목+저자의 책이 DB에 있는지 확인
            BookSearchDto book = existing.orElseGet(
                    () -> bookService.save(title, author, cover, description, category) // 없으면 새로 저장, 있으면 기존 book 사용
            );

            boolean registered = bookService.saveUserBook(userId, book.bookId()); // 사용자 - 도서 연결 (이미 등록된 경우 false 반환)

            if (registered) {
                rankingRedisService.incrementBookCount(userId); // Redis 독서량 +1
                dto = MsgDto.builder().result(1).msg("등록되었습니다.").bookId(book.bookId()).build();
            } else {
                dto = MsgDto.builder().result(0).msg("이미 등록된 책입니다.").bookId(book.bookId()).build(); // 이미 이 사용자가 등록한 책인 경우
            }

        } catch (Exception e) {
            log.error("책 등록 오류", e);
            dto = MsgDto.builder().result(0).msg("등록 중 오류가 발생했습니다.").build();
        }

        log.info("{}.registerBook End!", this.getClass().getName());
        return dto;
    }

    // [POST] /book/rating - 도서 별점 및 후기 저장
    // 별점 범위 검증 (1~5), 후기 글자수 제한 (300자)
    @PostMapping("/rating")
    @ResponseBody
    public MsgDto saveRating(
            @RequestParam Long bookId,                                            // 대상 도서 ID
            @RequestParam int rating,                                             // 별점 (1~5)
            @RequestParam(required = false, defaultValue = "") String content,    // 후기 내용
            HttpSession session) {

        log.info("{}.saveRating Start! - bookId:{}, rating:{}", this.getClass().getName(), bookId, rating);

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDto.builder().result(0).msg("로그인이 필요합니다.").build();
        if (rating < 1 || rating > 5) return MsgDto.builder().result(0).msg("별점은 1~5 사이여야 합니다.").build(); // 별점 유효성 검사

        try {
            String safeContent = content.length() > 300 ? content.substring(0, 300) : content; // 후기 300자 초과 시 잘라냄
            bookService.saveRating(userId, bookId, rating, safeContent); // 별점/후기 저장
            log.info("{}.saveRating End!", this.getClass().getName());
            return MsgDto.builder().result(1).msg("후기가 등록되었습니다.").build();
        } catch (Exception e) {
            log.error("후기 저장 오류", e);
            return MsgDto.builder().result(0).msg("후기 등록에 실패했습니다.").build();
        }
    }

    // [GET] /book/ratings?title=...&author=... - 특정 도서의 후기 목록 조회
    // 로그인한 경우 본인 후기 여부(isMine) 포함하여 반환
    @GetMapping("/ratings")
    @ResponseBody
    public ResponseEntity<?> getRatings(
            @RequestParam String title,  // 도서 제목
            @RequestParam String author, // 저자
            HttpSession session) {

        log.info("{}.getRatings Start! - title:{}", this.getClass().getName(), title);
        try {
            String userId = (String) session.getAttribute("SS_USER_ID"); // null이어도 허용 (비로그인 조회 가능)
            Optional<BookSearchDto> book = bookService.findByTitleAndAuthor(title, author); // 제목+저자로 도서 조회
            if (book.isEmpty()) return ResponseEntity.ok(java.util.Collections.emptyList()); // 없으면 빈 목록 반환
            List<BookRatingDto> ratings = bookService.getRatings(book.get().bookId(), userId); // 후기 목록 조회
            log.info("{}.getRatings End! - {}건", this.getClass().getName(), ratings.size());
            return ResponseEntity.ok(ratings);
        } catch (Exception e) {
            log.error("후기 조회 오류", e);
            return ResponseEntity.ok(java.util.Collections.emptyList()); // 오류 시에도 빈 목록 반환
        }
    }

    // [DELETE] /book/rating/{ratingId} - 내 후기 삭제
    // 본인 후기만 삭제 가능 (서비스 레이어에서 userId 검증)
    @DeleteMapping("/rating/{ratingId}")
    @ResponseBody
    public MsgDto deleteRating(
            @PathVariable Long ratingId, // URL 경로에서 후기 ID 추출
            HttpSession session) {

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDto.builder().result(0).msg("로그인이 필요합니다.").build();
        boolean deleted = bookService.deleteRating(ratingId, userId); // userId와 ratingId 매칭 확인 후 삭제 (권한 없으면 false)
        return deleted
                ? MsgDto.builder().result(1).msg("후기가 삭제되었습니다.").build()
                : MsgDto.builder().result(0).msg("삭제 권한이 없습니다.").build();
    }

    // [PATCH] /book/rating/{ratingId} - 내 후기 수정
    // 본인 후기만 수정 가능
    @PatchMapping("/rating/{ratingId}")
    @ResponseBody
    public MsgDto updateMyRating(
            @PathVariable Long ratingId,                                           // 수정할 후기 ID
            @RequestParam int rating,                                              // 새 별점
            @RequestParam(required = false, defaultValue = "") String content,     // 새 후기 내용
            HttpSession session) {

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDto.builder().result(0).msg("로그인이 필요합니다.").build();
        boolean updated = bookService.updateMyRating(ratingId, userId, rating, content); // userId 검증 후 수정 (권한 없으면 false)
        return updated
                ? MsgDto.builder().result(1).msg("후기가 수정되었습니다.").build()
                : MsgDto.builder().result(0).msg("수정 권한이 없습니다.").build();
    }

    // [GET] /book/my-ratings - 내가 작성한 후기 전체 조회
    @GetMapping("/my-ratings")
    @ResponseBody
    public ResponseEntity<?> getMyRatings(HttpSession session) {
        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(List.of()); // 비로그인 시 401 반환
        return ResponseEntity.ok(bookService.getMyRatings(userId)); // 내 후기 목록 반환
    }

    // [POST] /book/rating/{ratingId}/like - 후기 좋아요 토글
    // 이미 좋아요 → 취소, 좋아요 안 함 → 추가
    @PostMapping("/rating/{ratingId}/like")
    @ResponseBody
    public ResponseEntity<?> toggleLike(
            @PathVariable Long ratingId, // 좋아요 대상 후기 ID
            HttpSession session) {

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null)
            return ResponseEntity.status(401).body(
                    MsgDto.builder().result(0).msg("로그인이 필요합니다.").build()
            );
        return ResponseEntity.ok(bookService.toggleLike(ratingId, userId)); // 좋아요 상태 토글 후 현재 상태 반환
    }

    // [POST] /book/rating/{ratingId}/comment - 후기에 댓글 등록
    // 내용 공백 검사, 500자 초과 시 자동 절삭
    @PostMapping("/rating/{ratingId}/comment")
    @ResponseBody
    public MsgDto addComment(
            @PathVariable Long ratingId,  // 댓글 달 대상 후기 ID
            @RequestParam String content, // 댓글 내용
            HttpSession session) {

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDto.builder().result(0).msg("로그인이 필요합니다.").build();
        if (content == null || content.isBlank()) return MsgDto.builder().result(0).msg("댓글 내용을 입력해주세요.").build(); // 빈 댓글 방지
        String safe = content.length() > 500 ? content.substring(0, 500) : content; // 500자 초과 시 절삭
        bookService.addComment(ratingId, userId, safe); // 댓글 저장
        return MsgDto.builder().result(1).msg("댓글이 등록되었습니다.").build();
    }

    // [GET] /book/rating/{ratingId}/comments - 특정 후기의 댓글 목록 조회
    @GetMapping("/rating/{ratingId}/comments")
    @ResponseBody
    public ResponseEntity<?> getComments(@PathVariable Long ratingId) {
        return ResponseEntity.ok(bookService.getComments(ratingId)); // 댓글 목록 JSON 반환
    }

    // [유틸] cleanAuthor - 저자명 정제
    // 예: "김훈 (지은이), 박영선 (옮긴이)" → "김훈"
    private String cleanAuthor(String raw) {
        if (raw == null || raw.isBlank()) return ""; // null 또는 빈 문자열이면 빈 문자열 반환
        int parenIdx = raw.indexOf('(');                                         // '(' 위치 탐색
        String name = (parenIdx > 0 ? raw.substring(0, parenIdx) : raw).trim(); // 괄호 앞부분만 추출
        int commaIdx = name.indexOf(',');                                        // ',' 위치 탐색 (여러 저자 구분자)
        return (commaIdx > 0 ? name.substring(0, commaIdx) : name).trim();      // 첫 번째 저자만 반환
    }

    // [유틸] extractLeafCategory - 말단 카테고리 추출
    // 예: "국내도서>소설/시/희곡>한국소설" → "한국소설"
    private String extractLeafCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return ""; // null 방어 처리
        String[] parts = categoryName.split(">");   // '>' 구분자로 분리
        return parts[parts.length - 1].trim();      // 가장 구체적인 마지막 카테고리 반환
    }
}