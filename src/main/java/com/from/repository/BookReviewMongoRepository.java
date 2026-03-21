package com.from.repository;

import com.from.domain.BookReviewDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface BookReviewMongoRepository extends MongoRepository<BookReviewDocument, String> {
    List<BookReviewDocument> findByUserId(Long userId);
    List<BookReviewDocument> findByIsSent(int isSent);  // ← 이거 추가!
}