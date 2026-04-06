package com.from.service.impl;

import com.from.config.EncryptUtil;
import com.from.dto.UserInfoDTO;
import com.from.repository.UserInfoRepository;
import com.from.repository.entity.UserInfoEntity;
import com.from.service.EmailService;
import com.from.service.IUserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserInfoService implements IUserInfoService {

    private final UserInfoRepository userInfoRepository;
    private final EmailService emailService;

    // 아이디 중복 체크
    @Override
    public String checkUsernameExists(String username) throws Exception {
        log.info("{}.checkUsernameExists Start!", this.getClass().getName());
        String res = userInfoRepository.findByUserId(username).isPresent() ? "Y" : "N";
        log.info("{}.checkUsernameExists End!", this.getClass().getName());
        return res;
    }

    // 이메일 중복 체크 + 인증번호 발송
    @Override
    public String checkEmailAndSendCode(String email) throws Exception {
        log.info("{}.checkEmailAndSendCode Start!", this.getClass().getName());

        String encryptedEmail = EncryptUtil.encryptAES(email);
        boolean exists = userInfoRepository.findByEmail(encryptedEmail).isPresent();
        if (exists) return "DUPLICATE";

        String code = generateCode();
        emailService.sendVerificationCode(email, code, "SIGNUP");

        log.info("{}.checkEmailAndSendCode End!", this.getClass().getName());
        return code;
    }

    // 회원가입
    @Override
    public boolean signup(UserInfoDTO pDTO) throws Exception {
        log.info("{}.signup Start!", this.getClass().getName());

        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(pDTO.userId())
                .username(pDTO.username())
                .password(EncryptUtil.encryptSHA256(pDTO.password()))
                .name(pDTO.name())
                .email(EncryptUtil.encryptAES(pDTO.email()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userInfoRepository.save(pEntity);
        log.info("회원가입 완료: {}", pDTO.userId());

        log.info("{}.signup End!", this.getClass().getName());
        return userInfoRepository.findByUserId(pDTO.userId()).isPresent();
    }

    // 로그인
    @Override
    public UserInfoDTO login(String username, String password) throws Exception {
        log.info("{}.login Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        if (rEntity.isEmpty()) return null;

        UserInfoEntity user = rEntity.get();
        if (!EncryptUtil.encryptSHA256(password).equals(user.getPassword())) return null;

        UserInfoDTO rDTO = UserInfoDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(EncryptUtil.decryptAES(user.getEmail()))
                .build();

        log.info("{}.login End!", this.getClass().getName());
        return rDTO;
    }

    // 비밀번호 변경 (마이페이지)
    @Override
    public boolean changePassword(String userId, String newPassword) throws Exception {
        log.info("{}.changePassword Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return false;

        UserInfoEntity user = rEntity.get();
        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .password(EncryptUtil.encryptSHA256(newPassword))
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        userInfoRepository.save(pEntity);

        log.info("{}.changePassword End!", this.getClass().getName());
        return true;
    }

    // 비밀번호 변경 (비밀번호 찾기용)
    @Override
    public boolean changePasswordByUsername(String username, String newPassword) throws Exception {
        log.info("{}.changePasswordByUsername Start!", this.getClass().getName());
        boolean res = changePassword(username, newPassword);
        log.info("{}.changePasswordByUsername End!", this.getClass().getName());
        return res;
    }

    // 회원 탈퇴
    @Override
    public boolean deleteUser(String userId, String password) throws Exception {
        log.info("{}.deleteUser Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return false;
        if (!EncryptUtil.encryptSHA256(password).equals(rEntity.get().getPassword())) return false;

        userInfoRepository.delete(rEntity.get());

        log.info("{}.deleteUser End!", this.getClass().getName());
        return true;
    }

    // 아이디 찾기 - 인증번호 발송
    @Override
    public String findIdSendCode(String name, String email) throws Exception {
        log.info("{}.findIdSendCode Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByNameAndEmail(name, EncryptUtil.encryptAES(email));
        if (rEntity.isEmpty()) return "NOT_FOUND";

        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_ID");

        log.info("{}.findIdSendCode End!", this.getClass().getName());
        return code;
    }

    // 아이디 찾기 - 아이디 반환
    @Override
    public String findUsername(String name, String email) throws Exception {
        log.info("{}.findUsername Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByNameAndEmail(name, EncryptUtil.encryptAES(email));

        log.info("{}.findUsername End!", this.getClass().getName());
        return rEntity.map(UserInfoEntity::getUserId).orElse(null);
    }

    // 비밀번호 찾기 - 인증번호 발송
    @Override
    public String findPasswordSendCode(String username, String email) throws Exception {
        log.info("{}.findPasswordSendCode Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        if (rEntity.isEmpty()) return "NOT_FOUND";
        if (!EncryptUtil.decryptAES(rEntity.get().getEmail()).equals(email)) return "NOT_FOUND";

        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_PASSWORD");

        log.info("{}.findPasswordSendCode End!", this.getClass().getName());
        return code;
    }

    // 유저 정보 조회 (마이페이지·대시보드용, 이메일 복호화 포함)
    @Override
    public UserInfoDTO getUserInfo(String userId) throws Exception {
        log.info("{}.getUserInfo Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return null;

        UserInfoEntity user = rEntity.get();
        UserInfoDTO rDTO = UserInfoDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(EncryptUtil.decryptAES(user.getEmail()))
                .build();

        log.info("{}.getUserInfo End!", this.getClass().getName());
        return rDTO;
    }

    // 6자리 인증번호 생성
    private String generateCode() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}