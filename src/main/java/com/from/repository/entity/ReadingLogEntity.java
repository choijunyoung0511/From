package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "reading_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "read_date")
    private LocalDate readDate;
}