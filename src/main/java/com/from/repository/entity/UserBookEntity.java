package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * user_books 테이블과 매핑되는 JPA 엔티티.
 * 사용자(users)와 책(books)의 다대다(N:M) 관계를 연결하는 중간 테이블이다.
 *
 * 한 사용자가 여러 책을 등록할 수 있고,
 * 같은 책이 여러 사용자에게 등록될 수 있다.
 *
 * @EmbeddedId: 복합 기본키(userId + bookId)를 별도 클래스(UserBookId)로 분리하여 사용한다.
 */
@Entity
@Table(name = "user_books")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBookEntity {

    /** 복합 기본키 (userId + bookId). UserBookId 클래스에 정의되어 있다. */
    @EmbeddedId
    private UserBookId id;
}