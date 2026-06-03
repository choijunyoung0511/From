package com.from.repository;

import com.from.repository.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

//books테이블에 접근 하는 JPA
public interface BookRepository extends JpaRepository<BookEntity, Long> {

    //제목과 저자로 책을 찾음
    Optional<BookEntity> findByTitleAndAuthor(String title, String author);

    //특정 유저가 등록한 책 목록을 최신 등록순으로 조회,user_books 중간 테이블을 통해 유저-책 연결
    @Query("SELECT b FROM BookEntity b WHERE b.bookId IN " +
           "(SELECT ub.id.bookId FROM UserBookEntity ub WHERE ub.id.userId = :userId) " +
           "ORDER BY b.createdAt DESC")
    List<BookEntity> findByUserId(@Param("userId") String userId);
}