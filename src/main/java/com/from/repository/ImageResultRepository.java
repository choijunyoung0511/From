package com.from.repository;

import com.from.repository.entity.ImageResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * imge_results 테이블 접근 JPA Repository.
 * Spring Data JPA 가 런타임에 구현체를 자동 생성한다.
 */
public interface ImageResultRepository extends JpaRepository<ImageResult, Long> {

    /**
     * 특정 유저의 이미지 생성 결과를 최신순으로 조회한다.
     *
     * @param userId 로그인 ID (users.user_id VARCHAR)
     * @return 이미지 결과 목록 (생성일 내림차순)
     */
    List<ImageResult> findByUserIdOrderByCreateAtDesc(String userId);

    /**
     * 특정 유저의 특정 책에 대한 이미지 결과를 조회한다.
     *
     * @param bookId 책 ID
     * @param userId 로그인 ID
     * @return 일치하는 이미지 결과 목록
     */
    List<ImageResult> findByBookIdAndUserId(Long bookId, String userId);
}
