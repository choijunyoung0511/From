package com.from.mapper;

import com.from.domain.BookReview;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface BookReviewMapper {
    int insertReview(BookReview review);
    BookReview findById(Long reviewId);
    List<BookReview> findPendingReviews();
    int markAsSent(Long reviewId);
    List<BookReview> findByUserId(Long userId);  // 추가
}