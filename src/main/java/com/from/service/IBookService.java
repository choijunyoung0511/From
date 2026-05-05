package com.from.service;

import com.from.dto.BookSearchDTO;

import java.util.List;
import java.util.Optional;

public interface IBookService {

    // 제목 + 저자로 책 조회 (중복 등록 방지)
    Optional<BookSearchDTO> findByTitleAndAuthor(String title, String author);

    // 책 저장
    BookSearchDTO save(String title, String author, String coverImage, String description, String category);

    // 유저-책 연결 (이미 연결된 경우 false 반환)
    boolean saveUserBook(String userId, Long bookId);

    // ID로 책 단건 조회
    Optional<BookSearchDTO> findById(Long bookId);

    // 유저가 등록한 책 목록 조회 (최신순)
    List<BookSearchDTO> findByUserId(String userId);
}