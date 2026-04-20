package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_books")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBookEntity {

    @EmbeddedId
    private UserBookId id;
}