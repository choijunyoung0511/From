package com.from.service;

import com.from.config.EncryptUtil;
import com.from.domain.User;
import com.from.dto.user.SessionUser;
import com.from.dto.user.SignupRequestDto;
import com.from.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final EmailService emailService;

    // 아이디 중복 체크
    public String checkUsernameExists(String username) {
        return userMapper.checkUsernameExists(username);
    }

    // 이메일 중복 체크 + 인증번호 발송
    public String checkEmailAndSendCode(String email) {
        String encryptedEmail = EncryptUtil.encryptAES(email);
        String existsYn = userMapper.checkEmailExists(encryptedEmail);
        if ("Y".equals(existsYn)) return "DUPLICATE";
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "SIGNUP");
        return code;
    }

    // 회원가입
    public boolean signup(SignupRequestDto dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(EncryptUtil.encryptSHA256(dto.getPassword()));
        user.setName(dto.getName());
        user.setEmail(EncryptUtil.encryptAES(dto.getEmail()));
        int result = userMapper.insertUser(user);
        log.info("회원가입 결과: {}", result);
        return result == 1;
    }

    // 로그인
    public SessionUser login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) return null;
        if (!EncryptUtil.encryptSHA256(password).equals(user.getPassword())) return null;

        SessionUser su = new SessionUser();
        su.setUserId(user.getUserId());
        su.setUsername(user.getUsername());
        su.setName(user.getName());
        su.setEmail(EncryptUtil.decryptAES(user.getEmail()));
        log.info("로그인 성공: {}", username);
        return su;
    }

    // 비밀번호 변경 (로그인 상태 - 마이페이지)
    public boolean changePassword(Long userId, String newPassword) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword(EncryptUtil.encryptSHA256(newPassword));
        return userMapper.updatePassword(user) == 1;
    }

    // 비밀번호 변경 (아이디로 - 비밀번호 찾기용)
    public boolean changePasswordByUsername(String username, String newPassword) {
        User user = userMapper.findByUsername(username);
        if (user == null) return false;
        user.setPassword(EncryptUtil.encryptSHA256(newPassword));
        return userMapper.updatePassword(user) == 1;
    }

    // 회원 탈퇴
    public boolean deleteUser(Long userId, String password) {
        User user = userMapper.findByUserId(userId);
        if (user == null) return false;
        if (!EncryptUtil.encryptSHA256(password).equals(user.getPassword())) return false;
        return userMapper.deleteUser(userId) == 1;
    }

    // 아이디 찾기 - 인증번호 발송
    public String findIdSendCode(String name, String email) {
        User searchUser = new User();
        searchUser.setName(name);
        searchUser.setEmail(EncryptUtil.encryptAES(email));
        User user = userMapper.findByNameAndEmail(searchUser);
        if (user == null) return "NOT_FOUND";
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_ID");
        return code;
    }

    // 아이디 찾기 - 아이디 반환
    public String findUsername(String name, String email) {
        User searchUser = new User();
        searchUser.setName(name);
        searchUser.setEmail(EncryptUtil.encryptAES(email));
        User user = userMapper.findByNameAndEmail(searchUser);
        return user != null ? user.getUsername() : null;
    }

    // 비밀번호 찾기 - 인증번호 발송
    public String findPasswordSendCode(String username, String email) {
        User user = userMapper.findByUsername(username);
        if (user == null) return "NOT_FOUND";
        if (!EncryptUtil.decryptAES(user.getEmail()).equals(email)) return "NOT_FOUND";
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_PASSWORD");
        return code;
    }

    // 6자리 인증번호 생성
    private String generateCode() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}