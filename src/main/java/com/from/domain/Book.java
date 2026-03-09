package com.from.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class Book {
    private Long bookId;
    private String title;
    private String author;
    private String coverImage;  // cover → coverImage
    private LocalDateTime createdAt;
}