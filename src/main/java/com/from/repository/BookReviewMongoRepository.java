package com.from.repository;

import com.from.repository.document.BookReviewDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * aireviews MongoDB 컬렉션 접근 인터페이스.
 * Spring Data MongoDB 가 런타임에 구현체를 자동 생성한다.
 */
public interface BookReviewMongoRepository extends MongoRepository<BookReviewDocument, String> {

    /**
     * 특정 유저의 모든 독후감을 조회한다.
     *
     * @param userId 로그인 ID (users.user_id VARCHAR)
     * @return 해당 유저의 BookReviewDocument 목록
     */
    List<BookReviewDocument> findByUserId(String userId);

    /**
     * 이메일 발송 여부로 독후감을 필터링한다.
     *
     * @param isSent 0 = 미발송, 1 = 발송 완료
     * @return 조건에 맞는 BookReviewDocument 목록
     */
    List<BookReviewDocument> findByIsSent(int isSent);
}
