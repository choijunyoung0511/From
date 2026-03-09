package com.from.mapper;

import com.from.domain.BookReview;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface BookReviewMapper {
    int insertReview(BookReview review);
    BookReview findById(Long reviewId);
    List<BookReview> findPendingReviews(); // 발송 대상 조회
    int markAsSent(Long reviewId);         // 발송 완료 처리
}