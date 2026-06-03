package com.from.repository;

import com.from.repository.entity.ImageResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

//image_results 테이블에 접근
public interface ImageResultRepository extends JpaRepository<ImageResult, Long> {


    //특정 유저의 이미지 생성 결과를 최신순으로 조회
    List<ImageResult> findByUserIdOrderByCreateAtDesc(String userId);

    /**
     * 특정 유저의 특정 책에 대한 이미지 결과를 조회한다.
     *
     * @param bookId 책 ID
     * @param userId 로그인 ID
     * @return 일치하는 이미지 결과 목록
     */
    //특정 유저의 특정 책 에 대한 이미지 결과를 조회한다
    List<ImageResult> findByBookIdAndUserId(Long bookId, String userId);
}
