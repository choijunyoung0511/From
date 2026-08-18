package com.from.repository;

import com.from.repository.entity.BookRatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
//DB접근을 쉽게하기 위해서 사용. 기본 CRUD 자동 제공, SQL작성향 줄이고 자동 UPDATE
public interface BookRatingRepository extends JpaRepository<BookRatingEntity, Long> {
    List<BookRatingEntity> findByBookIdOrderByUpdatedAtDesc(Long bookId);
    Optional<BookRatingEntity> findByUserIdAndBookId(String userId, Long bookId);


    List<BookRatingEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByIdAndUserId(Long id, String userId);
}