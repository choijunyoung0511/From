package com.from.mapper;

import com.from.domain.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BookMapper {
    Book findByTitleAndAuthor(@Param("title") String title, @Param("author") String author);
    int insertBook(Book book);
    int insertUserBook(@Param("userId") Long userId, @Param("bookId") Long bookId);
    List<Book> findBooksByUserId(Long userId);
}