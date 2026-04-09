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

@Slf4j
@RequiredArgsConstructor
@Service
public class UserInfoService implements IUserInfoService {

    private final UserInfoRepository userInfoRepository;
    private final IEmailService emailService;

    // 아이디 중복체크
    //findByUSerId로 DB조회 -> 존재하면 "y" 없으면 "N" 반환

    @Override
    public String checkUsernameExists(String username) throws Exception {
        log.info("{}.checkUsernameExists Start!", this.getClass().getName());
        String res = userInfoRepository.findByUserId(username).isPresent() ? "Y" : "N";
        log.info("{}.checkUsernameExists End!", this.getClass().getName());
        return res;
    }

    // 아이디 중복 체크 + 인증번호 발송
    //이메일 AES 암호화후 DB조회
    //이미 존재하면 듀플리케이드 반환
    //없으면 6자리 인증번호 생성후에 이메일 발송,코드 반환

    @Override
    public String checkEmailAndSendCode(String email) throws Exception {
        log.info("{}.checkEmailAndSendCode Start!", this.getClass().getName());

        String encryptedEmail = EncryptUtil.encryptAES(email); //이메일 암호화
        boolean exists = userInfoRepository.findByEmail(encryptedEmail).isPresent();
        if (exists) return "DUPLICATE";

        String code = generateCode();
        emailService.sendVerificationCode(email, code, "SIGNUP");

        log.info("{}.checkEmailAndSendCode End!", this.getClass().getName());
        return code;
    }

    // 회원가입
    //DTO -> Entity변환 (빌더패턴)
    //비밀번호 SHA256 암호화, 이메일 AES 암호화 후 저장
    //저장 후 DB에서 재조회해서 성공 여부 반환
    @Override
    public boolean signup(UserInfoDTO pDTO) throws Exception {
        log.info("{}.signup Start!", this.getClass().getName());
        //DTO를 ENtity로 변환(빌더 패턴)
        //비밀번호 sha256 암호화, 이메일 AES 암호화후 저장
        //저장후 DB에서 재조회해서 성공 여부 반환
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
    //아이디로 DB조회
    //없으면 널 반환
    //입력 비밀번호를 SHA256으로 암호화 후 DB 저장값과 비교
    //일치하면 사용자 정보 DTO로 반환 (이메일은 복호화해서)

    @Override
    public UserInfoDTO login(String username, String password) throws Exception {
        log.info("{}.login Start!", this.getClass().getName());

        //DB에서 아이디 조회
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        //존재하지 않는 아이디
        if (rEntity.isEmpty()) return null;

        //옵셔널에서 실제 객체 꺼내기(에러가 발생하지 않도록 임시적인 타입을 변수에 담아두는 개념이다)
        UserInfoEntity user = rEntity.get();

        //입력 비밀번호를 암호화해서 DB 저장값과 비교 (단방향이라 복호화 불가)
        if (!EncryptUtil.encryptSHA256(password).equals(user.getPassword())) return null; //비밀번호 암호화


        //로그인 성공 -> 세션에 저장할 정보만 DTO로 구성
        //이메일은 AES 복호하해서 원문으로 반환
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
    //기존 Entity 정보를 유지하면서 비밀번호만 새로 암호화해서 덮어씀
    @Override
    public boolean changePassword(String userId, String newPassword) throws Exception {
        log.info("{}.changePassword Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return false;


        //기존 정보는 그대로 유지하고 비밀번호와 수정시각만 갱신
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


        //덮어쓰기 저장
        userInfoRepository.save(pEntity);

        log.info("{}.changePassword End!", this.getClass().getName());
        return true;
    }

    // 비밀번호 변경 (비밀번호 찾기용)
    //changePassword()를 그대로 재사용함
    @Override
    public boolean changePasswordByUsername(String username, String newPassword) throws Exception {
        log.info("{}.changePasswordByUsername Start!", this.getClass().getName());
        boolean res = changePassword(username, newPassword);
        log.info("{}.changePasswordByUsername End!", this.getClass().getName());
        return res;
    }

    // 회원 탈퇴
    //아이디로 조회
    //비밀번호 검증
    // 통과하면 DB에서 삭제
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
    //이름 + 이메일로 DB조회 -> 있으면 인증번호 발송
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
    //인증번호 검증은 컨트롤러에서 처리, -> 여기서는 아이디만 반환
    @Override
    public String findUsername(String name, String email) throws Exception {
        log.info("{}.findUsername Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByNameAndEmail(name, EncryptUtil.encryptAES(email));

        log.info("{}.findUsername End!", this.getClass().getName());
        return rEntity.map(UserInfoEntity::getUserId).orElse(null);
    }

    // 비밀번호 찾기 - 인증번호 발송
    //아이디로 조화 후 이메일 일치 여부 확인 -> 인증번호 발송
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
    // 이메일은 복호화해서 반환

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

    // 6자리 인증번호 생성 (내부 전용 private 메서드)
    //랜덤 숫자
    private String generateCode() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}