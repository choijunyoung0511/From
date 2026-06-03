package com.from.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

//기반으로 복합키 생성
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserBookId implements Serializable {

    //사용자 ID
    @Column(name = "user_id")
    private String userId;

    //책 ID
    @Column(name = "book_id")
    private Long bookId;
}