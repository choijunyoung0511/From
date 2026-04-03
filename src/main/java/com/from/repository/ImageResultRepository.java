//package com.from.repository;
//
//import com.from.domain.ImageResult;
//import org.springframework.data.jpa.repository.JpaRepository;
//import java.util.List;
//
//public interface ImageResultRepository extends JpaRepository<ImageResult, Long> {
//    List<ImageResult> findByUserIdOrderByCreateAtDesc(Long userId);
//    List<ImageResult> findByBookIdAndUserId(Long bookId, Long userId);
//}