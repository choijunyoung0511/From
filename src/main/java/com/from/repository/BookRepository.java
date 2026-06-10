package com.from.repository;

import com.from.repository.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

//books테이블에 접근 하는 JPA
//repository는 각 Entity와 연결되어 DB 접근을 담당하며, Service 계층에서 호출하여 사용합니다
//JPA와 Spring Data JPA가 자동으로 구현체를 생성해주기 때문입니다.
//개발자가 직접 SQL이나 구현 클래스를 작성하지 않아도 Spring Data JPA가 메서드 이름을 해석하여 자동으로 DB 조회를 수행하기 때문에 구현 코드가 없어도 동작합니다
public interface BookRepository extends JpaRepository<BookEntity, Long> {

    //제목과 저자로 책을 찾음
    Optional<BookEntity> findByTitleAndAuthor(String title, String author);

    //특정 유저가 등록한 책 목록을 최신 등록순으로 조회,user_books 중간 테이블을 통해 유저-책 연결
    @Query("SELECT b FROM BookEntity b WHERE b.bookId IN " +
           "(SELECT ub.id.bookId FROM UserBookEntity ub WHERE ub.id.userId = :userId) " +
           "ORDER BY b.createdAt DESC")
    List<BookEntity> findByUserId(@Param("userId") String userId);
}