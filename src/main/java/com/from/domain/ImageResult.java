package com.from.domain;

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
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 이미지 생성 결과를 저장하는 JPA 엔티티.
 * Gemini API 로 생성된 이미지를 base64 Data URL 형태로 DB에 저장한다.
 *
 * 주의: 테이블명 "imge_results" 는 초기 스키마 그대로 유지한다.
 */
@Entity
@Table(name = "imge_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageResult {

    /** 이미지 결과 고유 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long imageId;

    /** 연결된 책 ID */
    @Column(nullable = false)
    private Long bookId;

    /**
     * 생성 요청 사용자의 로그인 ID (users.user_id VARCHAR).
     * UserInfoEntity.userId 와 동일한 String 타입을 사용한다.
     */
    @Column(nullable = false, length = 20)
    private String userId;

    /** 사용자가 업로드한 원본 사진 S3 URL (inputs/ 폴더) */
    @Column(columnDefinition = "TEXT")
    private String inputImageUrl;

    /** Gemini API 가 생성한 결과 이미지 S3 URL (outputs/ 폴더) */
    @Column(columnDefinition = "LONGTEXT")
    private String imageUrl;

    /** 적용된 아트 스타일 (watercolor, cartoon 등) */
    @Column(length = 100)
    private String style;

    /** 생성 일시 (INSERT 시 자동 설정) */
    @Column(updatable = false)
    private LocalDateTime createAt;

    /**
     * 엔티티 저장 전 생성 일시를 자동으로 설정한다.
     */
    @PrePersist
    protected void onCreate() {
        this.createAt = LocalDateTime.now();
    }
}
