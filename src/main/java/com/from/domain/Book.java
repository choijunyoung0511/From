package com.from.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * books 테이블과 매핑되는 MyBatis 도메인 객체.
 * coverImage 필드는 map-underscore-to-camel-case 설정으로 cover_image 컬럼에 자동 매핑된다.
 */
@Getter
@Setter
public class Book {

    /** 책 고유 ID (auto-increment) */
    private Long bookId;

    /** 책 제목 */
    private String title;

    /** 저자 */
    private String author;

    /** 알라딘 API 에서 가져온 표지 이미지 URL */
    private String coverImage;

    /** 등록 일시 */
    private LocalDateTime createdAt;
}
