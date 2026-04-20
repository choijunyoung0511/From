package com.from.repository;

import com.from.repository.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<BookEntity, Long> {

    Optional<BookEntity> findByTitleAndAuthor(String title, String author);

    @Query("SELECT b FROM BookEntity b WHERE b.bookId IN " +
           "(SELECT ub.id.bookId FROM UserBookEntity ub WHERE ub.id.userId = :userId) " +
           "ORDER BY b.createdAt DESC")
    List<BookEntity> findByUserId(@Param("userId") String userId);
}