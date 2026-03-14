package com.from.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @Setter
public class BookReview {
    private Long reviewId;
    private Long userId;
    private Long bookId;
    private Integer paperId;
    private String emphasisContent;
    private String tone;
    private String deliveryDay;      // 기존 컬럼 유지
    private LocalDate deliveryDate;  // 추가
    private LocalTime deliveryTime;  // 추가
    private String aiContent;
    private String generationStatus;
    private int isSent;              // 추가
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String bookTitle;
    private String bookAuthor;
}