package com.from.repository;

import com.from.repository.entity.UserBookEntity;
import com.from.repository.entity.UserBookId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBookRepository extends JpaRepository<UserBookEntity, UserBookId> {
}