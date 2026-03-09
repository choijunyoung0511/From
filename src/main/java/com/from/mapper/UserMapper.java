package com.from.mapper;

import com.from.domain.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    // 아이디 중복 체크 (Y/N 반환)
    String checkUsernameExists(String username);

    // 이메일 중복 체크 (Y/N 반환)
    String checkEmailExists(String email);

    // 일반 회원가입
    int insertUser(User user);

    // 카카오 회원가입
    int insertKakaoUser(User user);

    // 로그인 (아이디로 회원 조회)
    User findByUsername(String username);

    // userId로 회원 조회
    User findByUserId(Long userId);

    // 카카오 ID로 회원 조회
    User findByKakaoId(Long kakaoId);

    // 회원 탈퇴 (deleted_at 업데이트)
    int deleteUser(Long userId);

    // 비밀번호 변경
    int updatePassword(User user);

    // 이름 + 이메일로 아이디 찾기
    User findByNameAndEmail(User user);
}