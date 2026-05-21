package com.from.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 사용자 정보를 담는 DTO.
 * Controller와 Service 사이, 또는 AJAX 응답에서 사용한다.
 *
 * 주요 사용처:
 * - 회원가입/로그인 파라미터 전달 (userId, password 등)
 * - 아이디 중복 체크 응답 (existsYn: "Y" 또는 "N")
 * - 아이디 찾기 응답 (userId 반환)
 * - 마이페이지 사용자 정보 표시 (userId, username, name, email)
 *
 * @JsonInclude(NON_DEFAULT): null인 필드는 JSON 응답에서 생략하여 응답을 간결하게 유지
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record UserInfoDTO(
        String userId,           // 로그인 아이디 (PK)
        String username,         // 닉네임
        String password,         // 비밀번호 (평문, 서비스 계층에서 SHA-256으로 암호화)
        String name,             // 실명
        String email,            // 이메일 (DB에는 AES-128-CBC 암호화 저장, DTO에서는 평문)
        String existsYn,           // 중복 여부: "Y" = 존재, "N" = 미존재
        LocalDateTime createdAt,   // 가입 일시
        LocalDateTime updatedAt,   // 최근 수정 일시
        LocalDateTime deletedAt,   // 탈퇴 일시 (소프트 삭제용, 현재 미사용)
        String profileImageUrl     // S3 프로필 이미지 URL (null이면 기본 아바타)
) {}