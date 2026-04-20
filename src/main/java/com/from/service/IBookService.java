package com.from.service;

import com.from.repository.entity.BookEntity;

import java.util.List;
import java.util.Optional;

public interface IBookService {

    // 제목 + 저자로 책 조회 (중복 등록 방지)
    Optional<BookEntity> findByTitleAndAuthor(String title, String author);

    // 책 저장
    BookEntity save(BookEntity book);

    // 유저-책 연결 (이미 연결된 경우 false 반환)
    boolean saveUserBook(String userId, Long bookId);

    // 유저가 등록한 책 목록 조회 (최신순)
    List<BookEntity> findByUserId(String userId);
}