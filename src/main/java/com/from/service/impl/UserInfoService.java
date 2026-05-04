package com.from.service.impl;

import com.from.config.EncryptUtil;          // SHA-256(단방향), AES-256(양방향) 암호화 유틸
import com.from.dto.UserInfoDTO;             // 컨트롤러 ↔ 서비스 간 데이터 전달 객체 (Record)
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
@Slf4j               // private static final Logger log = LoggerFactory.getLogger(UserInfoService.class); 와 동일
@RequiredArgsConstructor // 아래 두 final 필드를 생성자로 자동 주입 (= @Autowired 대체)
@Service             // 스프링 컨텍스트에 서비스 빈으로 등록
public class UserInfoService implements IUserInfoService {

    // ── 의존성 주입 (생성자 주입 – @RequiredArgsConstructor가 처리) ──────
    private final UserInfoRepository userInfoRepository; // DB CRUD 담당
    private final IEmailService emailService;            // 이메일 발송 담당


    //  1. 아이디 중복 체크

    /**
     * 회원가입 화면에서 아이디 입력 시 실시간 Ajax 중복 체크에 사용한다.
     *
     * @param username 사용자가 입력한 아이디 (평문)
     * @return "Y" = 이미 존재 (사용 불가) / "N" = 사용 가능
     *
     * 흐름: 입력값 → DB 조회 → 존재 여부 → "Y"/"N" 반환
     */
    @Override
    public String checkUsernameExists(String username) throws Exception {
        log.info("{}.checkUsernameExists Start!", this.getClass().getName());

        // findByUserId() 는 Optional<UserInfoEntity> 를 반환
        // isPresent() : 값이 있으면 true (= 아이디 이미 사용 중)
        // 삼항 연산자로 Y/N 문자열로 변환하여 반환
        String res = userInfoRepository.findByUserId(username).isPresent() ? "Y" : "N";

        log.info("{}.checkUsernameExists End!", this.getClass().getName());
        return res;
    }

    //  2. 이메일 중복 체크 + 인증번호 발송


    /**
     * 회원가입 시 이메일 입력 후 Ajax 호출로 실행된다.
     * 중복 이메일이 아닌 경우 6자리 인증번호를 이메일로 발송하고 코드 자체를 반환한다.
     * 컨트롤러에서 반환된 코드를 세션에 저장하여 추후 사용자 입력값과 비교한다.
     *
     * @param email 사용자가 입력한 이메일 (평문)
     * @return "DUPLICATE" = 중복 이메일 / 6자리 숫자 문자열 = 발송된 인증번호
     *
     * 흐름: 이메일 AES 암호화 → DB 조회 → 중복이면 종료
     *       → 신규면 인증번호 생성 → 이메일 발송 → 인증번호 반환
     */
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

    //  3. 회원가입


    /**
     * 이메일 인증까지 완료된 사용자의 가입을 처리한다.
     * 민감 정보를 암호화한 뒤 DB에 저장하고, 저장 성공 여부를 반환한다.
     *
     * @param pDTO 컨트롤러에서 전달된 사용자 입력 데이터 (평문 상태)
     * @return true = 가입 성공 / false = 가입 실패
     *
     * 흐름: DTO 수신 → 암호화 적용 → Entity 변환 → DB 저장 → 재조회로 성공 확인
     */
    @Override
    public boolean signup(UserInfoDTO pDTO) throws Exception {
        log.info("{}.signup Start!", this.getClass().getName());

        /*
         * DTO → Entity 변환
         * Builder 패턴을 사용하는 이유:
         *  - 어떤 필드에 어떤 값이 들어가는지 명시적으로 보임
         *  - 생성자 파라미터 순서 실수 방지
         *  - 일부 필드만 세팅할 때 유연함
         */
        UserInfoEntity pEntity = UserInfoEntity.builder()
                .userId(pDTO.userId())                            // 아이디 (평문 저장)
                .username(pDTO.username())                        // 닉네임 (평문 저장)
                .password(EncryptUtil.encryptSHA256(pDTO.password())) // 비밀번호: SHA-256 단방향 암호화
                .name(pDTO.name())                                // 이름 (평문 저장)
                .email(EncryptUtil.encryptAES(pDTO.email()))      // 이메일: AES-256 양방향 암호화
                .createdAt(LocalDateTime.now())                   // 가입 시각 (현재 시각 자동 기록)
                .updatedAt(LocalDateTime.now())                   // 수정 시각 (최초 가입 시 가입 시각과 동일)
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


    //  4. 로그인


    /**
     * 아이디/비밀번호를 검증하고 로그인 성공 시 사용자 정보를 반환한다.
     * 비밀번호는 단방향이므로 입력값을 동일하게 암호화하여 해시 비교한다.
     *
     * @param username 입력된 아이디
     * @param password 입력된 비밀번호 (평문)
     * @return 로그인 성공 → UserInfoDTO(이메일 복호화 포함) / 실패 → Optional.empty()
     *
     * 흐름: userId 조회 → 없으면 실패
     *       → 비밀번호 SHA-256 암호화 후 DB 해시와 비교 → 다르면 실패
     *       → 성공 시 이메일 복호화 후 DTO 반환
     */
    @Override
    public UserInfoDTO login(String username, String password) throws Exception {
        log.info("{}.login Start!", this.getClass().getName());

        // 1단계: 아이디로 DB 조회 → 없는 아이디면 즉시 null 반환
        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(username);
        if (rEntity.isEmpty()) return null;

        UserInfoEntity user = rEntity.get();

        // 2단계: 비밀번호 SHA-256 암호화 후 DB 저장값과 비교
        if (!EncryptUtil.encryptSHA256(password).equals(user.getPassword())) return null;

        // 3단계: 로그인 성공 → 이메일 복호화 후 DTO 반환
        UserInfoDTO rDTO = UserInfoDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(EncryptUtil.decryptAES(user.getEmail()))
                .build();

        log.info("{}.login End!", this.getClass().getName());
        return rDTO;
    }

    //  5. 비밀번호 변경 (마이페이지 – 로그인 상태에서 사용)

    /**
     * 로그인된 사용자가 마이페이지에서 비밀번호를 변경할 때 사용한다.
     * JPA Entity는 불변성을 권장하므로, 기존 정보를 유지하면서
     * 비밀번호와 updatedAt만 바꾼 새 Entity를 save()로 덮어쓴다.
     *
     * @param userId      변경할 대상의 아이디
     * @param newPassword 새 비밀번호 (평문)
     * @return true = 변경 성공 / false = 해당 userId 없음
     *
     * 흐름: userId 조회 → 기존 정보 유지 → 비밀번호·updatedAt만 교체 → DB 덮어쓰기
     */
    @Override
    public boolean changePassword(String userId, String newPassword) throws Exception {
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
                .userId(user.getUserId())                              // 기존 값 유지 (PK)
                .username(user.getUsername())                          // 기존 값 유지
                .password(EncryptUtil.encryptSHA256(newPassword))      //  새 비밀번호 SHA-256 암호화
                .name(user.getName())                                  // 기존 값 유지
                .email(user.getEmail())                                // 기존 값 유지 (이미 암호화된 상태)
                .createdAt(user.getCreatedAt())                        // 기존 가입일 유지
                .updatedAt(LocalDateTime.now())                        // ★ 수정 시각 갱신
                .build();

        userInfoRepository.save(pEntity); // PK 동일 → UPDATE 실행

        log.info("{}.changePassword End!", this.getClass().getName());
        return true;
    }


    //  6. 비밀번호 변경 (비밀번호 찾기용 – 비로그인 상태)


    /**
     * 비밀번호 찾기 화면에서 인증 완료 후 새 비밀번호를 설정할 때 사용한다.
     * 로직이 마이페이지 변경과 동일하므로 내부적으로 changePassword()를 위임 호출한다.
     *
     * @param username    대상 아이디
     * @param newPassword 새 비밀번호 (평문)
     * @return changePassword() 결과 그대로 반환
     *
     * 설계 의도: 두 케이스의 인터페이스를 분리(메서드명 명시)하여
     *            컨트롤러에서 "어떤 시나리오인지" 명확하게 드러나도록 함
     */
    @Override
    public boolean changePasswordByUsername(String username, String newPassword) throws Exception {
        log.info("{}.changePasswordByUsername Start!", this.getClass().getName());

        // 실질적인 로직은 changePassword()에 위임 (코드 재사용)
        boolean res = changePassword(username, newPassword);

        log.info("{}.changePasswordByUsername End!", this.getClass().getName());
        return res;
    }


    //  7. 회원 탈퇴 (하드 삭제)

    /**
     * 비밀번호를 재확인하여 본인 인증 후 계정을 영구 삭제한다.
     * 소프트 삭제(is_deleted 플래그)가 아닌 DB 레코드 자체를 제거하는 하드 삭제 방식이다.
     *
     * @param userId   탈퇴할 사용자의 아이디
     * @param password 입력된 현재 비밀번호 (평문)
     * @return true = 탈퇴 성공 / false = 조회 실패 또는 비밀번호 불일치
     *
     * 흐름: userId 조회 → 비밀번호 재검증 → DB 레코드 삭제
     */
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


    /**
     * 이름 + 이메일로 가입 여부를 확인하고 인증번호를 발송한다.
     * 인증번호 검증은 컨트롤러의 세션 비교로 처리한다.
     *
     * @param name  사용자가 입력한 이름
     * @param email 사용자가 입력한 이메일 (평문)
     * @return "NOT_FOUND" = 일치하는 유저 없음 / 6자리 코드 = 발송 완료
     *
     * 흐름: 이메일 AES 암호화 → 이름+이메일로 DB 조회 → 없으면 종료
     *       → 인증번호 생성 → 이메일 발송 → 코드 반환
     */
    @Override
    public String findIdSendCode(String name, String email) throws Exception {
        log.info("{}.findIdSendCode Start!", this.getClass().getName());

        // DB 저장값은 암호화된 이메일이므로, 조회 전 동일하게 AES 암호화
        Optional<UserInfoEntity> rEntity =
                userInfoRepository.findByNameAndEmail(name, EncryptUtil.encryptAES(email));

        if (rEntity.isEmpty()) return "NOT_FOUND"; // 이름 또는 이메일 불일치

        // 유저 확인 완료 → 인증번호 생성 및 발송
        String code = generateCode();
        emailService.sendVerificationCode(email, code, "FIND_ID"); // "FIND_ID" 타입으로 템플릿 구분

        log.info("{}.findIdSendCode End!", this.getClass().getName());
        return code; // 컨트롤러가 세션(findIdCode)에 저장
    }


    //  9. 아이디 찾기 – 아이디 반환

    /**
     * 인증번호 검증 완료 후 실제 아이디를 조회하여 반환한다.
     * 인증번호 검증(세션 비교)은 컨트롤러에서 이미 완료된 상태에서 이 메서드가 호출된다.
     *
     * @param name  사용자가 입력한 이름
     * @param email 사용자가 입력한 이메일 (평문)
     * @return 아이디가 담긴 Optional / 없으면 Optional.empty()
     *
     * 흐름: 이메일 AES 암호화 → DB 조회 → Entity에서 userId 추출
     */
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


    /**
     * 아이디와 이메일로 본인을 확인하고 인증번호를 발송한다.
     * 아이디 찾기(이름+이메일)와 달리 아이디+이메일 조합으로 검증한다.
     *
     * @param username 사용자가 입력한 아이디
     * @param email    사용자가 입력한 이메일 (평문)
     * @return "NOT_FOUND" = 불일치 / 6자리 코드 = 발송 완료
     *
     * 흐름: userId 조회 → 없으면 종료
     *       → DB 암호화 이메일 복호화 후 입력값과 비교 → 불일치면 종료
     *       → 인증번호 생성 → 발송 → 코드 반환
     */
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
        emailService.sendVerificationCode(email, code, "FIND_PASSWORD");

        log.info("{}.findPasswordSendCode End!", this.getClass().getName());
        return code;
    }

    //  11. 유저 정보 조회 (마이페이지·대시보드)

    /**
     * 로그인 세션에 저장된 userId로 상세 정보를 조회한다.
     * 이메일은 복호화하여 평문으로 반환한다.
     *
     * @param userId 조회할 사용자의 아이디
     * @return 사용자 정보 DTO / 없으면 Optional.empty()
     */
    @Override
    public Optional<UserInfoDTO> getUserInfo(String userId) throws Exception {
        log.info("{}.getUserInfo Start!", this.getClass().getName());

        Optional<UserInfoEntity> rEntity = userInfoRepository.findByUserId(userId);
        if (rEntity.isEmpty()) return Optional.empty();

        UserInfoEntity user = rEntity.get();

        // Entity → DTO 변환 (이메일 복호화 포함)
        UserInfoDTO rDTO = UserInfoDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(EncryptUtil.decryptAES(user.getEmail())) // 화면 표시용 복호화
                .build();

        log.info("{}.getUserInfo End!", this.getClass().getName());
        return Optional.of(rDTO);
    }

    //  Private 헬퍼 메서드


    private String generateCode() {
        // 100000 ~ 999999 범위로 고정하여 0으로 시작하는 5자리 코드 방지
        return String.valueOf(100000 + new Random().nextInt(900000));
    }
}