package com.from.service.impl;

import com.from.config.EncryptUtil;          // SHA-256(단방향), AES-256(양방향) 암호화 유틸
import com.from.dto.UserInfoDto;             // 컨트롤러 ↔ 서비스 간 데이터 전달 객체 (Record)
import com.from.repository.UserInfoRepository; // JPA Repository (DB 접근)
import com.from.repository.entity.UserInfoEntity; // DB 테이블과 매핑되는 Entity
import com.from.service.IEmailService;       // 이메일 발송 인터페이스
import com.from.service.IUserInfoService;    // 이 서비스가 구현해야 할 인터페이스
import lombok.RequiredArgsConstructor; // final 필드를 생성자 주입으로 자동 생성
import lombok.extern.slf4j.Slf4j;     // log.info() 사용을 위한 Logger 자동 생성
import org.springframework.stereotype.Service; // 스프링 빈으로 등록 (서비스 계층 명시)
import java.time.LocalDateTime; // 생성일·수정일 기록
import java.util.Optional;      // null 대신 사용하는 안전한 래퍼 타입
import java.util.Random;        // 인증번호 난수 생성

/**
 * 암호화 정책
 *  · 비밀번호 : SHA-256  (단방향 – 복호화 불가, 비교만 가능)
 *  · 이메일   : AES-256  (양방향 – 복호화하여 화면에 표시 가능)
 */
//sha 단방향 비밀번호, aes 양방향 이메일 화면에도 표시해야됨
@Slf4j               // private static final Logger log = LoggerFactory.getLogger(UserInfoService.class); 와 동일
@RequiredArgsConstructor // 아래 두 final 필드를 생성자로 자동 주입 (= @Autowired 대체)
@Service             // 스프링 컨텍스트에 서비스 빈으로 등록
public class UserInfoService implements IUserInfoService {

    // ── 의존성 주입 (생성자 주입 – @RequiredArgsConstructor가 처리) ──────
    private final UserInfoRepository userInfoRepository; // DB CRUD 담당
    private final IEmailService emailService;            // 이메일 발송 담당


    // [회원가입-3] 아이디 중복 체크
    // signup.html checkUsername() → POST /user/checkUsername → 이 메서드
    // DB에서 userId로 조회 → 존재하면 "Y"(중복), 없으면 "N"(사용 가능) 반환
    @Override
    public String checkUsernameExists(String username) throws Exception {
        log.info("{}.checkUsernameExists Start! username={}", this.getClass().getName(), username);
        String res = userInfoRepository.findByUserId(username).isPresent() ? "Y" : "N";
        log.info("{}.checkUsernameExists End! exists={}", this.getClass().getName(), res);
        return res;
    }

    // [회원가입-4] 이메일 중복 체크 + 인증번호 발송
    // signup.html sendEmailCode() → POST /user/checkEmail → 이 메서드
    // AES로 이메일 암호화 → DB 중복 확인 → 신규이면 6자리 코드 생성·발송 → 코드 반환
    // 반환된 코드는 컨트롤러에서 세션(emailCode)에 저장됨
    @Override
    public String checkEmailAndSendCode(String email) throws Exception {
        log.info("{}.checkEmailAndSendCode Start!", this.getClass().getName());

        // DB에는 암호화된 이메일이 저장되어 있으므로
        // 입력값도 동일한 방식(AES)으로 암호화해야 일치 여부 비교 가능
        String encryptedEmail = EncryptUtil.encryptAES(email);

        // 암호화된 이메일로 DB 조회 → 이미 가입된 이메일이면 중복 처리
        boolean exists = userInfoRepository.findByEmail(encryptedEmail).isPresent();
        if (exists) return "DUPLICATE"; // 조기 반환(Early Return)으로 중첩 if 방지

        // 신규 이메일: 6자리 인증번호 생성
        String code = generateCode(); // → 내부 메서드 참조 (파일 하단에 정의)

        // 이메일 발송 (타입을 "SIGNUP"으로 전달하면 이메일 템플릿/제목이 달라짐)
        emailService.sendVerificationCode(email, code, "SIGNUP");

        log.info("{}.checkEmailAndSendCode End!", this.getClass().getName());
        return code; // 컨트롤러가 이 값을 세션(emailCode)에 저장함
    }

    // [회원가입-6] 회원가입 DB 저장
    // UserInfoController.signup() → 이 메서드
    // 비밀번호 SHA-256 + 이메일 AES 암호화 → UserInfoEntity 빌드 → JPA save() → DB INSERT
    @Override
    public boolean signup(UserInfoDto pDTO) throws Exception {
        log.info("{}.signup Start! userId={}, username={}", this.getClass().getName(), pDTO.userId(), pDTO.username());

         //DTO -> 엔터티 반환 Builder 패턴을 사용하는 이유: 어떤 값이 들어가는지 명시적으로 보임,생성자 파라미터 순서 실수 방지, 일부 필드만 세팅할떄 유연함
        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(pDTO.userId()) //아이디
                .username(pDTO.username()) //유저 이름
                .password(EncryptUtil.encryptSHA256(pDTO.password())) //비밀번호 단방향
                .name(pDTO.name()) //이름
                .email(EncryptUtil.encryptAES(pDTO.email()))
                .profileImageUrl(pDTO.profileImageUrl())          // 프로필 이미지 URL (null이면 기본 아바타) 나머지는 아마존 s3에다가 프로필 사진 저장
                .createdAt(LocalDateTime.now()) // 생성 시각
                .updatedAt(LocalDateTime.now()) // 수정된 시각
                .build();

        // JPA save(): Entity에 @Id 값이 있으면 INSERT, 이미 존재하면 UPDATE
        // 여기서는 신규 userId이므로 INSERT 실행
        userInfoRepository.save(pEntity);
        log.info("회원가입 완료: {}", pDTO.userId());

        // save() 는 void가 아닌 Entity를 반환하지만,
        // 실제로 DB에 저장됐는지 재조회로 2중 확인하는 방어적 패턴
        log.info("{}.signup End!", this.getClass().getName());
        return userInfoRepository.findByUserId(pDTO.userId()).isPresent();
    }


    // [로그인-6] 로그인 검증
    // login.html doLogin() → POST /user/loginProc → UserInfoController.loginProc() → 이 메서드
    @Override
    public UserInfoDto login(String username, String password) throws Exception {
        log.info("{}.login Start!", this.getClass().getName());

        // [로그인-6] 1단계: 아이디로 DB 조회 → 없는 아이디면 즉시 null 반환
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        if (rEntity.isEmpty()) return null;

        UserInfoEntity user = rEntity.get();

        // [로그인-6] 2단계: 입력 비밀번호를 SHA-256 해시 후 DB 저장 해시와 비교
        if (!EncryptUtil.encryptSHA256(password).equals(user.getPassword())) return null;

        // [로그인-7] 3단계: 검증 성공 → AES 이메일 복호화 후 UserInfoDto 반환
        //            컨트롤러에서 이 DTO를 받아 세션에 SS_USER_ID·SS_USER_NAME 저장
        UserInfoDto rDTO = UserInfoDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(EncryptUtil.decryptAES(user.getEmail()))
                .build();

        log.info("{}.login End!", this.getClass().getName());
        return rDTO;
    }

    //  5. 비밀번호 변경 (마이페이지 – 로그인 상태에서 사용)

    @Override
    public boolean changePassword(String userId, String newPassword) throws Exception {
        //성공 실패만 필요하기 떄문에 boolean타입 쓴거임
        log.info("{}.changePassword Start!", this.getClass().getName());

        // 사용자 존재 여부 확인
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return false; // 존재하지 않는 userId

        UserInfoEntity user = rEntity.get(); // 기존 Entity 언래핑

        /*
         *  핵심 패턴: 전체 필드를 다시 채워서 save() 호출
         *
         * JPA의 save()는 PK(userId)가 같으면 UPDATE를 실행한다.
         * 변경하지 않는 필드(userId, username, name, email, createdAt)는
         * 기존 Entity에서 그대로 복사하고, password와 updatedAt만 새 값으로 교체한다.
         */
        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .password(EncryptUtil.encryptSHA256(newPassword))
                .name(user.getName())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())  // 프로필 이미지 유지 (누락 시 null로 덮어쓰기 방지)
                .createdAt(user.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        userInfoRepository.save(pEntity); // PK 동일 → UPDATE 실행
        //update query 안쓴 이유: jpa의 save는 PK가 동일하면 insert가 아니라 update수행 그래서 기존 pk를 유지한 상태로
        // Entity생성해 save()를 호출

        log.info("{}.changePassword End!", this.getClass().getName());
        return true;
    }


    //  6. 비밀번호 변경 (비밀번호 찾기용 – 비로그인 상태)

    @Override
    public boolean changePasswordByUsername(String username, String newPassword) throws Exception {
        log.info("{}.changePasswordByUsername Start!", this.getClass().getName());

        // 실질적인 로직은 changePassword()에 위임 (코드 재사용)
        boolean res = changePassword(username, newPassword);

        log.info("{}.changePasswordByUsername End!", this.getClass().getName());
        return res;
    }


    //  7. 회원 탈퇴
    @Override
    public boolean deleteUser(String userId, String password) throws Exception {
        log.info("{}.deleteUser Start!", this.getClass().getName());

        // 1단계: 아이디로 유저 조회
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return false; // 존재하지 않는 계정

        // 2단계: 비밀번호 재검증 (탈퇴 전 본인 확인)
        // SHA-256 해시 비교로 입력 비밀번호와 DB 저장 해시 일치 여부 확인
        if (!EncryptUtil.encryptSHA256(password).equals(rEntity.get().getPassword())) {
            return false; // 비밀번호 불일치 → 탈퇴 거부
        }

        // 3단계: 실제 DB 레코드 삭제 (복구 불가)
        userInfoRepository.delete(rEntity.get());

        log.info("{}.deleteUser End!", this.getClass().getName());
        return true;
    }

    //  8. 아이디 찾기 – 인증번호 발송



    //이메일AES 암호화 -> 이름+이메일로 DB조회 -> 없으면종료
    // 인증번호 생성 -> 이메일 발송 -> 코드반환
    @Override
    public String findIdSendCode(String name, String email) throws Exception {
        log.info("{}.findIdSendCode Start!", this.getClass().getName());

        // DB 저장값은 암호화된 이메일이므로, 조회 전 동일하게 AES 암호화
        Optional<UserInfoEntity> rEntity =
                userInfoRepository.findByNameAndEmail(name, EncryptUtil.encryptAES(email));

        if (rEntity.isEmpty()) return "NOT_FOUND"; // 이름 또는 이메일 불일치

        // 유저 확인 완료 → 인증번호 생성 및 발송
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_ID"); // 아이디 찾기 email서비스 로직에서 기능 별로 분리해둠

        log.info("{}.findIdSendCode End!", this.getClass().getName());
        return code; // 컨트롤러가 세션(findIdCode)에 저장
    }


    //  9 . 아이디 찾기 – 아이디 반환


    @Override
    public Optional<String> findUsername(String name, String email) throws Exception {
        log.info("{}.findUsername Start!", this.getClass().getName());

        // findByNameAndEmail() 결과 Optional에서 map()으로 userId만 추출
        // Entity가 없으면 map()이 실행되지 않아 Optional.empty()가 자동 반환
        Optional<String> result = userInfoRepository
                .findByNameAndEmail(name, EncryptUtil.encryptAES(email))
                .map(UserInfoEntity::getUserId); // 메서드 참조로 간결하게 표현

        log.info("{}.findUsername End!", this.getClass().getName());
        return result;
    }



    //  10. 비밀번호 찾기 – 인증번호 발송


    @Override
    public String findPasswordSendCode(String username, String email) throws Exception {
        log.info("{}.findPasswordSendCode Start!", this.getClass().getName());

        // 1단계: 아이디로 유저 조회
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        if (rEntity.isEmpty()) return "NOT_FOUND";

        // 2단계: 이메일 추가 검증
        // DB에는 AES 암호화된 이메일이 저장되어 있으므로 decryptAES() 후 비교
        // ※ encryptAES() 비교가 아닌 decryptAES() 비교를 사용하는 이유:
        //    AES는 같은 평문도 IV(초기화벡터)에 따라 암호문이 달라질 수 있어
        //    암호문끼리 비교하면 같은 이메일도 불일치로 나올 수 있기 때문
        if (!EncryptUtil.decryptAES(rEntity.get().getEmail()).equals(email)) {
            return "NOT_FOUND"; // 이메일 불일치
        }

        // 3단계: 인증번호 생성 및 발송
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_PASSWORD"); //비밀번호 찾기

        log.info("{}.findPasswordSendCode End!", this.getClass().getName());
        return code;
    }

    //  11. 유저 정보 조회 (마이페이지·대시보드)


    @Override
    public Optional<UserInfoDto> getUserInfo(String userId) throws Exception {
        log.info("{}.getUserInfo Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return Optional.empty();

        UserInfoEntity user = rEntity.get();

        // Entity → DTO 변환 (이메일 복호화 포함) 빌더 로직
        UserInfoDto rDTO = UserInfoDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(EncryptUtil.decryptAES(user.getEmail()))
                .profileImageUrl(user.getProfileImageUrl())
                .build();

        log.info("{}.getUserInfo End!", this.getClass().getName());
        return Optional.of(rDTO);
    }

    @Override
    public void updateProfileImage(String userId, String imageUrl) throws Exception {
        log.info("{}.updateProfileImage Start! - userId:{}", this.getClass().getName(), userId);
        // Setter 없이 JPQL UPDATE로 직접 필드 수정
        userInfoRepository.updateProfileImageUrl(userId, imageUrl);
        log.info("{}.updateProfileImage End!", this.getClass().getName());
    }

    //  Private 헬퍼 메서드,인증번호 설정


    private String generateCode() {
        // 100000 ~ 999999 범위로 고정하여 0으로 시작하는 5자리 코드 방지, 랜덤 인증번호임!
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}