package com.from.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserBookId implements Serializable {

    @Column(name = "user_id")
    private String userId;

    @Column(name = "book_id")
    private Long bookId;
}