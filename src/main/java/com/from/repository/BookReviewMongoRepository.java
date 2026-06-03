package com.from.repository;

import com.from.repository.document.BookReviewDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;


//몽고 컬랙션 인터페이스
public interface BookReviewMongoRepository extends MongoRepository<BookReviewDocument, String> {

    //특정 유저의 모든 독후감을 조회
    List<BookReviewDocument> findByUserId(String userId);

    //이메일 발송여부로 독후감을 필터링
    List<BookReviewDocument> findByIsSent(int isSent);
}
