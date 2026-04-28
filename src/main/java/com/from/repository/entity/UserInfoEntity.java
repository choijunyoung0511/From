package com.from.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;

/**
 * users 테이블과 매핑되는 JPA 엔티티.
 * 회원 정보를 MySQL에 저장하고 조회한다.
 *
 * @DynamicInsert: INSERT 시 null 필드는 SQL에서 제외하여 DB 기본값을 사용하게 함
 * @DynamicUpdate: UPDATE 시 변경된 컬럼만 포함하여 불필요한 업데이트를 방지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@DynamicInsert
@Builder
@Entity
@Table(name = "users")
public class UserInfoEntity {

    /** 로그인 아이디 (기본키). 자동 증가 없이 사용자가 직접 입력한 값을 사용한다. */
    @Id
    @Column(name = "user_id", length = 20)
    private String userId;

    /** 닉네임 (화면 표시용 이름) */
    @Column(name = "username", length = 20)
    private String username;

    /** SHA-256 + 솔트로 암호화된 비밀번호. 복호화 불가하여 로그인 시 같은 방식으로 암호화하여 비교한다. */
    @Column(name = "password", length = 255)
    private String password;

    /** 실명 (아이디 찾기에 사용) */
    @Column(name = "name", length = 50)
    private String name;

    /** AES-128-CBC로 암호화된 이메일. DB에는 암호문으로 저장되고, 화면에는 복호화하여 표시한다. */
    @Column(name = "email", length = 100)
    private String email;

    /** 계정 생성 일시. updatable=false로 최초 저장 후 변경되지 않는다. */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 최근 정보 수정 일시 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 탈퇴 일시 (소프트 삭제용, 현재 미사용 — 하드 삭제 방식으로 운영 중) */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}