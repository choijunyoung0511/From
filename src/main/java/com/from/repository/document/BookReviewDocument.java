package com.from.repository.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
//초기에는 기능 구현과 프론트 연동 속도를 우선하기 위해 Entity를 그대로 반환했지만,
// 이후 유지보수성과 응답 안정성을 위해 DTO 분리가 필요하다고 판단했습니다 이 부분을 개선 포인트로 인지하겠습니다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "aireviews")
public class BookReviewDocument {

    @Id
    private String id;

    private String userId;

    private Long bookId;

    private Integer paperId;

    private String emphasisContent;

    private String tone;

    private LocalDate deliveryDate;

    private LocalTime deliveryTime;

    private String aiContent;

    private String generationStatus;


    //이메일 발송여부
    private int isSent;

    private String bookTitle;

    private String bookAuthor;

    @CreatedDate
    private LocalDateTime createdAt;

    //발송 완료 처리
    public void markAsSent() {
        this.isSent = 1;
    }
}