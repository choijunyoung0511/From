package com.from.service;

import com.from.dto.UserInfoDTO;

public interface IUserInfoService {

    // 아이디 중복 체크
    String checkUsernameExists(String username) throws Exception;

    // 이메일 중복 체크 + 인증번호 발송
    String checkEmailAndSendCode(String email) throws Exception;

    // 회원가입
    boolean signup(UserInfoDTO pDTO) throws Exception;

    // 로그인
    UserInfoDTO login(String username, String password) throws Exception;

    // 비밀번호 변경 (마이페이지)
    boolean changePassword(String userId, String newPassword) throws Exception;

    // 비밀번호 변경 (비밀번호 찾기용)
    boolean changePasswordByUsername(String username, String newPassword) throws Exception;

    // 회원 탈퇴
    boolean deleteUser(String userId, String password) throws Exception;

    // 아이디 찾기 - 인증번호 발송
    String findIdSendCode(String name, String email) throws Exception;

    // 아이디 찾기 - 아이디 반환
    String findUsername(String name, String email) throws Exception;

    // 비밀번호 찾기 - 인증번호 발송
    String findPasswordSendCode(String username, String email) throws Exception;
}