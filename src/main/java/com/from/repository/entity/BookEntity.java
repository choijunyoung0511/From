package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * books 테이블과 매핑되는 JPA 엔티티.
 * 알라딘 API로 검색한 책 정보를 저장한다.
 * 같은 책(제목+저자)은 중복 저장하지 않고 여러 유저가 공유한다.
 */
@Entity
@Table(name = "books")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookEntity {

    /** 책 고유 ID. DB가 자동으로 증가시킨다(AUTO_INCREMENT). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    /** 책 제목 */
    @Column(name = "title")
    private String title;

    /** 저자명 */
    @Column(name = "author")
    private String author;

    /** 알라딘 API가 제공하는 표지 이미지 URL */
    @Column(name = "cover_image")
    private String coverImage;

    /** 최초 등록 일시. updatable=false로 변경되지 않는다. 대시보드의 독서 날짜로 사용된다. */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 엔티티 최초 저장 시 생성 일시를 자동으로 설정한다.
     * @PrePersist: INSERT 직전에 JPA가 자동으로 호출한다.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}