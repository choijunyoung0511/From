package com.from.repository;

import com.from.repository.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * books 테이블에 접근하는 JPA Repository.
 */
public interface BookRepository extends JpaRepository<BookEntity, Long> {

    /**
     * 제목과 저자로 책을 조회한다.
     * 책 등록 시 중복 확인에 사용한다. 같은 책은 한 번만 저장하고 여러 유저가 공유한다.
     *
     * @param title  책 제목
     * @param author 저자명
     * @return 일치하는 책 (없으면 Optional.empty())
     */
    Optional<BookEntity> findByTitleAndAuthor(String title, String author);

    /**
     * 특정 유저가 등록한 책 목록을 최신 등록순으로 조회한다.
     * user_books 중간 테이블을 통해 유저-책 연결을 확인한다.
     *
     * JPQL(Java Persistence Query Language): 테이블명 대신 엔티티 클래스명을 사용하는 JPA 전용 쿼리 언어
     *
     * @param userId 사용자 로그인 ID
     * @return 해당 유저가 등록한 책 목록 (최신순)
     */
    @Query("SELECT b FROM BookEntity b WHERE b.bookId IN " +
           "(SELECT ub.id.bookId FROM UserBookEntity ub WHERE ub.id.userId = :userId) " +
           "ORDER BY b.createdAt DESC")
    List<BookEntity> findByUserId(@Param("userId") String userId);
}