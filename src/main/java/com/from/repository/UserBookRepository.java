package com.from.repository;

import com.from.repository.entity.UserBookEntity;
import com.from.repository.entity.UserBookId;
import org.springframework.data.jpa.repository.JpaRepository;


//user_books 테이블에 접근하는 JPA
public interface UserBookRepository extends JpaRepository<UserBookEntity, UserBookId> {
}