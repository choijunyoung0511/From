package com.from.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * MongoDB aireviews 컬렉션과 매핑되는 도큐먼트.
 * AI 독후감 생성 결과를 저장하며, isSent=0 인 항목은
 * ReviewScheduler 가 예약 시각에 이메일로 발송한다.
 */
@Getter
@Setter
@Document(collection = "aireviews")
public class BookReviewDocument {

    /** MongoDB 도큐먼트 ID */
    @Id
    private String id;

    /**
     * 작성자 로그인 ID (users.user_id VARCHAR).
     * UserInfoEntity 의 PK 와 동일한 String 타입을 사용한다.
     */
    private String userId;

    /** 연결된 책 ID (books.book_id) */
    private Long bookId;

    /** 편지지 테마 번호 (1~12) */
    private Integer paperId;

    /** 독후감에서 강조할 내용 (사용자 입력) */
    private String emphasisContent;

    /** 작성 톤 (FORMAL / CASUAL / EMOTIONAL / ANALYTICAL) */
    private String tone;

    /** 발송 예약 날짜 */
    private LocalDate deliveryDate;

    /** 발송 예약 시각 */
    private LocalTime deliveryTime;

    /** GPT 가 생성한 독후감 본문 */
    private String aiContent;

    /** 생성 상태 (COMPLETED 등) */
    private String generationStatus;

    /**
     * 이메일 발송 여부.
     * 0 = 미발송, 1 = 발송 완료
     */
    private int isSent;

    /** 책 제목 (이메일 표시용 캐싱) */
    private String bookTitle;

    /** 저자 (이메일 표시용 캐싱) */
    private String bookAuthor;

    /** 도큐먼트 생성 일시 (@EnableMongoAuditing 필요) */
    @CreatedDate
    private LocalDateTime createdAt;
}
