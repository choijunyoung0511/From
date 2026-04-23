package com.from.service.impl;

import com.from.config.EncryptUtil;
import com.from.dto.UserInfoDTO;
import com.from.repository.UserInfoRepository;
import com.from.repository.entity.UserInfoEntity;
import com.from.service.IEmailService;
import com.from.service.IUserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * 사용자 인증·계정 관리 서비스 구현체.
 * 회원가입, 로그인, 비밀번호 변경, 아이디/비밀번호 찾기, 탈퇴 기능을 처리한다.
 * 비밀번호는 SHA-256(단방향), 이메일은 AES-256(양방향) 암호화를 적용한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserInfoService implements IUserInfoService {

    private final UserInfoRepository userInfoRepository;
    private final IEmailService emailService;

    /**
     * 아이디 중복 체크.
     * DB에 같은 userId가 있으면 "Y", 없으면 "N"을 반환한다.
     * 회원가입 화면에서 아이디 입력 후 실시간 중복 확인 AJAX에 사용한다.
     */
    @Override
    public String checkUsernameExists(String username) throws Exception {
        log.info("{}.checkUsernameExists Start!", this.getClass().getName());
        String res = userInfoRepository.findByUserId(username).isPresent() ? "Y" : "N";
        log.info("{}.checkUsernameExists End!", this.getClass().getName());
        return res;
    }

    /**
     * 이메일 중복 체크 후 인증번호를 발송한다.
     * 이메일을 AES 암호화하여 DB에 저장된 암호문과 비교한다.
     * 이미 가입된 이메일이면 "DUPLICATE"를 반환하고, 신규이면 6자리 인증번호를 발송·반환한다.
     */
    @Override
    public String checkEmailAndSendCode(String email) throws Exception {
        log.info("{}.checkEmailAndSendCode Start!", this.getClass().getName());

        // DB에는 암호화된 이메일이 저장되므로 같은 방식으로 암호화하여 비교
        String encryptedEmail = EncryptUtil.encryptAES(email);
        boolean exists = userInfoRepository.findByEmail(encryptedEmail).isPresent();
        if (exists) return "DUPLICATE";

        // 신규 이메일: 인증번호 생성 후 이메일 발송
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "SIGNUP");

        log.info("{}.checkEmailAndSendCode End!", this.getClass().getName());
        return code;
    }

    /**
     * 회원가입을 처리한다.
     * DTO → Entity 변환 시 비밀번호를 SHA-256, 이메일을 AES-256으로 암호화하여 저장한다.
     * 저장 후 DB에서 재조회하여 성공 여부를 반환한다.
     */
    @Override
    public boolean signup(UserInfoDTO pDTO) throws Exception {
        log.info("{}.signup Start!", this.getClass().getName());

        // DTO를 Entity로 변환하면서 민감 정보 암호화
        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(pDTO.userId())
                .username(pDTO.username())
                .password(EncryptUtil.encryptSHA256(pDTO.password())) // 단방향 암호화
                .name(pDTO.name())
                .email(EncryptUtil.encryptAES(pDTO.email()))          // 양방향 암호화
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userInfoRepository.save(pEntity);
        log.info("회원가입 완료: {}", pDTO.userId());

        // 저장 후 재조회하여 실제로 DB에 삽입됐는지 확인
        log.info("{}.signup End!", this.getClass().getName());
        return userInfoRepository.findByUserId(pDTO.userId()).isPresent();
    }

    /**
     * 로그인을 처리한다.
     * 1. userId로 DB 조회 → 없으면 null 반환
     * 2. 입력 비밀번호를 SHA-256으로 암호화하여 DB 저장값과 비교
     * 3. 일치하면 UserInfoDTO(이메일 복호화 포함)를 반환
     */
    @Override
    public UserInfoDTO login(String username, String password) throws Exception {
        log.info("{}.login Start!", this.getClass().getName());

        // DB에서 아이디 조회
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        if (rEntity.isEmpty()) return null; // 존재하지 않는 아이디

        UserInfoEntity user = rEntity.get();

        // 입력 비밀번호를 암호화하여 DB 저장값과 비교 (단방향이라 복호화 불가)
        if (!EncryptUtil.encryptSHA256(password).equals(user.getPassword())) return null;

        // 로그인 성공: 세션에 저장할 정보만 DTO로 구성 (이메일은 복호화하여 원문으로)
        UserInfoDTO rDTO = UserInfoDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(EncryptUtil.decryptAES(user.getEmail()))
                .build();

        log.info("{}.login End!", this.getClass().getName());
        return rDTO;
    }

    /**
     * 비밀번호를 변경한다 (마이페이지용).
     * 기존 사용자 정보를 그대로 유지하면서 비밀번호와 updatedAt만 갱신하여 덮어쓴다.
     */
    @Override
    public boolean changePassword(String userId, String newPassword) throws Exception {
        log.info("{}.changePassword Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return false;

        // 기존 정보를 유지하고 비밀번호와 수정 시각만 갱신 (Builder 패턴으로 새 Entity 생성 후 덮어쓰기)
        UserInfoEntity user = rEntity.get();
        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .password(EncryptUtil.encryptSHA256(newPassword)) // 새 비밀번호 SHA-256 암호화
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        userInfoRepository.save(pEntity);

        log.info("{}.changePassword End!", this.getClass().getName());
        return true;
    }

    /**
     * 비밀번호를 변경한다 (비밀번호 찾기용).
     * 내부적으로 changePassword()를 재사용한다.
     */
    @Override
    public boolean changePasswordByUsername(String username, String newPassword) throws Exception {
        log.info("{}.changePasswordByUsername Start!", this.getClass().getName());
        boolean res = changePassword(username, newPassword);
        log.info("{}.changePasswordByUsername End!", this.getClass().getName());
        return res;
    }

    /**
     * 회원을 탈퇴(하드 삭제)한다.
     * 아이디로 조회 후 비밀번호를 검증하고, 통과하면 DB에서 레코드를 삭제한다.
     */
    @Override
    public boolean deleteUser(String userId, String password) throws Exception {
        log.info("{}.deleteUser Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return false;

        // 비밀번호 검증 (SHA-256 암호화 후 비교)
        if (!EncryptUtil.encryptSHA256(password).equals(rEntity.get().getPassword())) return false;

        userInfoRepository.delete(rEntity.get());

        log.info("{}.deleteUser End!", this.getClass().getName());
        return true;
    }

    /**
     * 아이디 찾기: 이름과 이메일로 유저를 확인하고 인증번호를 발송한다.
     * 이메일을 AES 암호화하여 DB 저장값과 비교한다.
     * 일치하는 유저가 없으면 "NOT_FOUND"를 반환한다.
     */
    @Override
    public String findIdSendCode(String name, String email) throws Exception {
        log.info("{}.findIdSendCode Start!", this.getClass().getName());

        // 이름 + 암호화된 이메일로 DB 조회
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByNameAndEmail(name, EncryptUtil.encryptAES(email));
        if (rEntity.isEmpty()) return "NOT_FOUND";

        // 유저 확인 완료: 인증번호 발송
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_ID");

        log.info("{}.findIdSendCode End!", this.getClass().getName());
        return code;
    }

    /**
     * 아이디 찾기: 인증번호 검증 완료 후 아이디(userId)를 반환한다.
     * 인증번호 검증은 컨트롤러에서 세션 비교로 처리하고, 여기서는 아이디만 반환한다.
     */
    @Override
    public String findUsername(String name, String email) throws Exception {
        log.info("{}.findUsername Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByNameAndEmail(name, EncryptUtil.encryptAES(email));

        log.info("{}.findUsername End!", this.getClass().getName());
        return rEntity.map(UserInfoEntity::getUserId).orElse(null);
    }

    /**
     * 비밀번호 찾기: 아이디와 이메일로 유저를 확인하고 인증번호를 발송한다.
     * 아이디로 먼저 조회 후, 이메일 일치 여부를 추가 검증한다.
     */
    @Override
    public String findPasswordSendCode(String username, String email) throws Exception {
        log.info("{}.findPasswordSendCode Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        if (rEntity.isEmpty()) return "NOT_FOUND";

        // DB의 암호화된 이메일을 복호화하여 입력 이메일과 비교
        if (!EncryptUtil.decryptAES(rEntity.get().getEmail()).equals(email)) return "NOT_FOUND";

        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_PASSWORD");

        log.info("{}.findPasswordSendCode End!", this.getClass().getName());
        return code;
    }

    /**
     * 유저 정보를 조회한다 (마이페이지·대시보드용).
     * DB에 암호화된 이메일을 복호화하여 반환한다.
     */
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
                .email(EncryptUtil.decryptAES(user.getEmail())) // 이메일 복호화
                .build();

        log.info("{}.getUserInfo End!", this.getClass().getName());
        return rDTO;
    }

    /**
     * 6자리 랜덤 숫자 인증번호를 생성한다.
     * 100000~999999 범위의 난수를 생성하여 항상 6자리를 보장한다.
     */
    private String generateCode() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}