package com.from.service;

import com.from.dto.UserInfoDto;

import java.util.Optional;

public interface IUserInfoService {

    // 아이디 중복 체크
    String checkUsernameExists(String username) throws Exception;

    // 이메일 중복 체크 + 인증번호 발송
    String checkEmailAndSendCode(String email) throws Exception;

    // 회원가입
    boolean signup(UserInfoDto pDTO) throws Exception;

    // 로그인 (아이디·비밀번호 불일치 시 null)
    UserInfoDto login(String username, String password) throws Exception;

    // 비밀번호 변경 (마이페이지)
    boolean changePassword(String userId, String newPassword) throws Exception;

    // 비밀번호 변경 (비밀번호 찾기용)
    boolean changePasswordByUsername(String username, String newPassword) throws Exception;

    // 회원 탈퇴
    boolean deleteUser(String userId, String password) throws Exception;

    // 아이디 찾기 - 인증번호 발송
    String findIdSendCode(String name, String email) throws Exception;

    // 아이디 찾기 - 아이디 반환 (유저 없으면 empty)
    Optional<String> findUsername(String name, String email) throws Exception;

    // 비밀번호 찾기 - 인증번호 발송
    String findPasswordSendCode(String username, String email) throws Exception;

    // 유저 정보 조회 (마이페이지·대시보드용, 유저 없으면 empty)
    Optional<UserInfoDto> getUserInfo(String userId) throws Exception;

    // 프로필 이미지 URL 업데이트
    void updateProfileImage(String userId, String imageUrl) throws Exception;
}