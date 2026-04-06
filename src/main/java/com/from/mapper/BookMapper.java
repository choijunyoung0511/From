package com.from.mapper;

import com.from.domain.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 책 관련 MyBatis 매퍼 인터페이스.
 * SQL 은 resources/mapper/BookMapper.xml 에 정의되어 있다.
 */
@Mapper
public interface BookMapper {

    /**
     * 제목과 저자로 책을 조회한다 (중복 등록 방지용).
     *
     * @param title  책 제목
     * @param author 저자
     * @return 일치하는 Book, 없으면 null
     */
    Book findByTitleAndAuthor(@Param("title") String title, @Param("author") String author);

    /**
     * 새 책을 books 테이블에 등록한다.
     * 저장 후 bookId 에 auto-increment 값이 채워진다.
     *
     * @param book 등록할 Book 객체
     * @return 영향받은 행 수
     */
    int insertBook(Book book);

    /**
     * 유저와 책을 user_books 테이블에 연결한다.
     * 이미 연결된 경우 IGNORE 처리된다.
     *
     * @param userId 로그인 ID (users.user_id VARCHAR)
     * @param bookId 연결할 책 ID
     * @return 영향받은 행 수
     */
    int insertUserBook(@Param("userId") String userId, @Param("bookId") Long bookId);

    /**
     * 특정 유저가 등록한 책 목록을 최신순으로 조회한다.
     *
     * @param userId 로그인 ID (users.user_id VARCHAR)
     * @return 등록된 Book 목록
     */
    List<Book> findBooksByUserId(String userId);
}
