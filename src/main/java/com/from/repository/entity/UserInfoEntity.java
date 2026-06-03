package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;




//user 테이블과 매핑되는 JPA 엔터티
@Getter
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@DynamicInsert
@Builder
@Entity
@Table(name = "users")
public class UserInfoEntity {

    //로그인 아이디
    @Id
    @Column(name = "user_id", length = 20)
    private String userId;

    //닉네임
    @Column(name = "username", length = 20)
    private String username;
    //비밀번호 sha256
    @Column(name = "password", length = 255)
    private String password;

    //실제이름
    @Column(name = "name", length = 50)
    private String name;

    //이메일 AES
    @Column(name = "email", length = 100)
    private String email;

    //계정 생성일시
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    //최근 정보 수정 일시
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //탈퇴 일시
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    //s3이미지 URL
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;
}