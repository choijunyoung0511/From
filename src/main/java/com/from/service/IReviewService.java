package com.from.service;

import com.from.domain.BookReviewDocument;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * AI 독후감 생성 서비스 인터페이스.
 * ReviewController가 이 인터페이스를 통해 서비스를 호출한다.
 * 구현체는 ReviewService(service/impl/)에 있다.
 */
public interface IReviewService {

    /**
     * 독후감 생성 결과를 담는 내부 레코드.
     * generateAndSave()가 생성된 독후감 본문과 책 정보를 함께 반환할 때 사용한다.
     */
    record ReviewResult(
            String content,    // GPT가 생성한 독후감 본문
            String bookTitle,  // 책 제목 (결과 화면 표시용)
            String bookAuthor  // 저자명 (결과 화면 표시용)
    ) {}

    /**
     * GPT API로 독후감을 생성하고 MongoDB에 저장한다.
     *
     * @param userId       요청 사용자의 로그인 ID
     * @param bookId       독후감을 작성할 책의 ID
     * @param emphasis     독후감에서 강조할 내용 (사용자 입력)
     * @param tone         작성 톤 (FORMAL, CASUAL, EMOTIONAL, ANALYTICAL)
     * @param deliveryDate 미래에 이메일로 받을 날짜
     * @param deliveryTime 미래에 이메일로 받을 시각
     * @param paperId      편지지 테마 번호 (1~12)
     * @return 생성된 독후감 내용과 책 정보
     */
    ReviewResult generateAndSave(String userId, Long bookId, String emphasis, String tone,
                                  LocalDate deliveryDate, LocalTime deliveryTime, int paperId);

    /**
     * 유저의 독후감 이력을 조회한다 (마이페이지용).
     *
     * @param userId 요청 사용자의 로그인 ID
     * @return MongoDB aireviews 컬렉션의 독후감 목록
     */
    List<BookReviewDocument> getReviewsByUserId(String userId);
}