package com.from.repository;

import com.from.repository.entity.UserBookEntity;
import com.from.repository.entity.UserBookId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * user_books 테이블에 접근하는 JPA Repository.
 * 사용자-책 연결 관계(N:M 중간 테이블)를 관리한다.
 *
 * 기본키 타입이 복합키(UserBookId)이므로 JpaRepository<UserBookEntity, UserBookId>로 선언한다.
 * existsById(UserBookId)로 이미 등록된 책인지 중복 확인에 활용한다.
 */
public interface UserBookRepository extends JpaRepository<UserBookEntity, UserBookId> {
}