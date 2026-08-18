package com.from.repository;

import com.from.repository.entity.BookReviewCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 독서 리뷰 댓글 데이터를 처리하는 DB 접근계층
//JPA는 매서드 이름 규칙을 보고 자동으로 쿼리를 생성해준다.(매서드 이름이 쿼리엳할을 해준다)
//UPDATE/DELETE 같은 수정 쿼리면 @Query를 쓰는 경우가 많음.

public interface BookReviewCommentRepository extends JpaRepository<BookReviewCommentEntity, Long> {
    List<BookReviewCommentEntity> findByRatingIdOrderByCreatedAtAsc(Long ratingId);
    long countByRatingId(Long ratingId);
}