package com.from.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * user_books 테이블의 복합 기본키를 정의하는 클래스.
 * userId(사용자 ID) + bookId(책 ID)의 조합으로 행을 유일하게 식별한다.
 *
 * @Embeddable: 이 클래스를 다른 엔티티(UserBookEntity)에 포함(embed)할 수 있음을 선언한다.
 * Serializable: JPA 스펙에 따라 복합키 클래스는 직렬화 가능해야 한다.
 * @EqualsAndHashCode: JPA가 기본키 동등 비교에 사용하므로 반드시 구현해야 한다.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserBookId implements Serializable {

    /** 사용자 로그인 ID (users.user_id 참조) */
    @Column(name = "user_id")
    private String userId;

    /** 책 ID (books.book_id 참조) */
    @Column(name = "book_id")
    private Long bookId;
}