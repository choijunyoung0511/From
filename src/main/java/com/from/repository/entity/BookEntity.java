package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

//DB테이블을 자바 객체로 표현한 클래스(객체중심 개발)
//영속화 = JPA가 객체를 DB와 연결해서 관리하는것
//books테이블과 매핑되는 엔터티 알라딘 API로 검색한 책 정보를 저장, 같은책은 저장하지 않고 여러 유저가 공유
//BookEntity는 책 정보를 객체 중심으로 관리하기 위해 사용한 Entity이며, JPA 영속화를 통해 DB의 books 테이블과 연결되어 동작합니다.
//민감한 정보 방지. db와 직접 연결할떄는 엔터티 데이터를 전달할떄는 DTO

@Entity//DB테이블과 매핑되는 Entity로 인식
@Table(name = "books") //DB
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookEntity {

    @Id //PK
    @GeneratedValue(strategy = GenerationType.IDENTITY)     //ID,DB가 자동으로 증가

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

    //ISBN13 (알라딘 검색 결과 등록 시 저장. 기존 데이터는 null일 수 있음 - 도서관 소장 조회에 사용)
    @Column(name = "isbn13", length = 20)
    private String isbn13;

    //최초 등록시 독서 날짜로 사용
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    //엔터티 최초 저장 시 생성 일시를 자동으로 설정
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}