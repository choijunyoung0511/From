package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;


//한 사용자가 여러 책을 등록 할 수있고, 같은 책이 여러 사용자에게 등롣될수 있다.
@Entity
@Table(name = "user_books")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBookEntity {

    //복합키
    @EmbeddedId
    private UserBookId id;
}