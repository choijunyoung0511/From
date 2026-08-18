package com.from.repository;

import com.from.repository.entity.BookReviewLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;



//DB와 연결해서 좋아요 데이터를 저장,조회,삭제하는 레포지토리
//좋아요 기능에서 중복 좋아요 확인, 좋아요 취소, 좋아요 개수 조회를 위해 만든 Repository입니다. 쿼리는 직접 작성하지 않았고 메서드 이름 기반으로 JPA가 자동 생성합니다.
public interface BookReviewLikeRepository extends JpaRepository<BookReviewLikeEntity, Long> {
    boolean existsByRatingIdAndUserId(Long ratingId, String userId); //좋아요를 이미 눌렀는지 확인
    long countByRatingId(Long ratingId);
    Optional<BookReviewLikeEntity> findByRatingIdAndUserId(Long ratingId, String userId);
}