package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

//DB테이블을 자바 객체로 표현한 클래스(객체중심 개발)
//영속화 = JPA가 객체를 DB와 연결해서 관리하는
//books테이블과 매핑되는 엔터티 알라딘 API로 검색한 책 정보를 저장, 같은책은 저장하지 않고 여러 유저가 공유
@Entity
@Table(name = "books")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookEntity {

    //ID,DB가 자동으로 증가
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;


    //책 제목
    @Column(name = "title")
    private String title;

    //저자
    @Column(name = "author")
    private String author;

    //이미지
    @Column(name = "cover_image")
    private String coverImage;

    //줄거리
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;


    //카테고리
    @Column(name = "category")
    private String category;

    //최초 등록시 독서 날짜로 사용
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    //엔터티 최초 저장 시 생성 일시를 자동으로 설정
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}