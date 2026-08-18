package com.from.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이미지 생성 결과를 저장하는 JPA 엔티티.
 * Gemini API 로 생성된 이미지를 S3에 업로드하고 그 URL을 DB에 저장한다.
 *
 * 주의: 테이블명 "imge_results" 는 초기 스키마 그대로 유지한다.
 */
@Entity
@Table(name = "imge_results")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long imageId;

    @Column(nullable = false)
    private Long bookId;

    @Column(nullable = false, length = 20)
    private String userId;

    //원본사진 URL input
    @Column(columnDefinition = "TEXT")
    private String inputImageUrl;

    //결과사진 URL outputs
    @Column(columnDefinition = "LONGTEXT")
    private String imageUrl;

    @Column(length = 100)
    private String style;

    @Column(updatable = false)
    private LocalDateTime createAt;

    @PrePersist
    protected void onCreate() {
        this.createAt = LocalDateTime.now();
    }
}