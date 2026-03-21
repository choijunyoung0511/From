// com/from/domain/BookReviewDocument.java
package com.from.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @Setter
@Document(collection = "aireviews")
public class BookReviewDocument {

    @Id
    private String id;
    private Long userId;
    private Long bookId;
    private Integer paperId;
    private String emphasisContent;
    private String tone;
    private String deliveryDay;
    private LocalDate deliveryDate;
    private LocalTime deliveryTime;
    private String aiContent;
    private String generationStatus;
    private int isSent;
    private String bookTitle;
    private String bookAuthor;

    @CreatedDate
    private LocalDateTime createdAt;
}