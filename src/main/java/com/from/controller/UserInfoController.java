// UserInfoController.java - 사용자 인증·계정·마이페이지·대시보드를 처리하는 컨트롤러
// /user/** 경로에 매핑되며, 인증이 필요한 경로는 LoginInterceptor 가 자동으로 검사한다
package com.from.controller;

import com.from.repository.document.BookReviewDocument; // MongoDB 독후감 도큐먼트
import com.from.dto.ImageResponseDto;  // 이미지 생성 결과 응답 DTO
import com.from.dto.MsgDto;            // 공통 응답 메시지 DTO (result + msg)
import com.from.dto.UserInfoDto;       // 사용자 정보 DTO
import com.from.dto.BookSearchDto;     // 도서 검색 결과 DTO
import com.from.service.IBookService;      // 도서 비즈니스 로직 서비스 인터페이스
import com.from.service.IImageService;     // 이미지 생성 서비스 인터페이스
import com.from.service.IReviewService;    // 독후감 생성 서비스 인터페이스
import com.from.service.IS3UploadService;  // S3 파일 업로드 서비스 인터페이스
import com.from.service.IUserInfoService;  // 사용자 인증·계정 서비스 인터페이스
import org.springframework.web.multipart.MultipartFile; // 파일 업로드 처리
import com.from.util.CmmUtil;              // null 안전 처리 유틸 (nvl 등)
import jakarta.servlet.http.Cookie;                  // 쿠키 만료 처리용
import jakarta.servlet.http.HttpServletRequest;      // HTTP 요청 파라미터 추출용
import jakarta.servlet.http.HttpServletResponse;     // 쿠키 응답 설정용
import jakarta.servlet.http.HttpSession;             // HTTP 세션 (로그인 사용자 확인용)
import lombok.RequiredArgsConstructor;               // final 필드 기반 생성자 자동 생성
import lombok.extern.slf4j.Slf4j;                    // SLF4J 로거 자동 생성
import org.springframework.http.ResponseEntity;      // HTTP 상태코드 포함 응답 객체
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;                 // 템플릿에 데이터 전달용
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;                      // 날짜+시간 처리
import java.time.format.DateTimeFormatter;           // 날짜 포맷 지정
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j                      // log.info(), log.error() 등 로거 사용 가능하게 해주는 어노테이션
@Controller                 // Spring MVC 컨트롤러로 등록 (View 이름 반환 가능)
@RequestMapping("/user")    // 이 컨트롤러의 모든 URL은 /user 로 시작
@RequiredArgsConstructor    // final 필드를 인자로 받는 생성자 자동 생성 (의존성 주입)
public class UserInfoController {

    private final IUserInfoService userInfoService; // 사용자 인증·계정 서비스
    private final IReviewService reviewService;     // GPT 독후감 생성 및 조회 서비스
    private final IBookService bookService;         // 도서 DB 처리 서비스
    private final IImageService imageService;       // 이미지 생성 및 조회 서비스
    private final IS3UploadService s3UploadService; // S3 파일 업로드 서비스

    // [GET] /user/signup - 회원가입 화면 반환
    // GET: URL 에 데이터를 포함하지 않고 페이지만 요청하는 방식
    @GetMapping("/signup")
    public String signupForm() {
        log.info("{}.user/signup Start!", this.getClass().getName());
        log.info("{}.user/signup End!", this.getClass().getName());
        return "user/signup"; // templates/user/signup.html 반환
    }

    // [POST] /user/checkUsername - 아이디 중복 체크 (비동기 AJAX)
    // POST: 데이터를 HTTP 바디에 담아 전송하는 방식 (GET 보다 보안이 높음)
    // 반환: existsYn = "Y" (이미 사용 중) / "N" (사용 가능)
    @ResponseBody // 반환값을 JSON 으로 직렬화하여 응답 body 에 담음
    @PostMapping("/checkUsername")
    public UserInfoDto checkUsername(HttpServletRequest request) throws Exception {
        log.info("{}.checkUsername Start!", this.getClass().getName());

        String username = CmmUtil.nvl(request.getParameter("username")); // null 이면 빈 문자열로 안전하게 처리
        log.info("username : {}", username);

        String existsYn = userInfoService.checkUsernameExists(username); // 해당 아이디가 이미 존재하는지 확인 ("Y"/"N" 반환)

        UserInfoDto rDTO = UserInfoDto.builder()
                .existsYn(existsYn)
                .build(); // 빌더 패턴으로 응답 DTO 생성 후 반환 (JSON 으로 자동 직렬화)

        log.info("{}.checkUsername End!", this.getClass().getName());
        return rDTO;
    }

    // [POST] /user/checkEmail - 이메일 중복 체크 + 인증번호 발송 (비동기 AJAX)
    // 이미 가입된 이메일이면 existsYn="Y", 신규 이메일이면 인증번호를 발송하고 세션에 저장
    @ResponseBody
    @PostMapping("/checkEmail")
    public UserInfoDto checkEmail(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.checkEmail Start!", this.getClass().getName());

        String email = CmmUtil.nvl(request.getParameter("email")); // null 이면 빈 문자열로 안전하게 처리
        log.info("email : {}", email);

        String code = userInfoService.checkEmailAndSendCode(email); // 이메일 중복 확인 후 인증번호 발송

        UserInfoDto rDTO;
        if ("DUPLICATE".equals(code)) {
            rDTO = UserInfoDto.builder().existsYn("Y").build(); // 중복 이메일: 이미 사용 중임을 알림
        } else {
            // 인증번호 발송 성공: 세션에 인증번호, 발송 시각, 대상 이메일 저장
            session.setAttribute("emailCode",     code);                // 발송된 인증번호
            session.setAttribute("emailCodeTime", LocalDateTime.now()); // 발송 시각 (만료 시간 계산용)
            session.setAttribute("emailTarget",   email);               // 인증 대상 이메일
            rDTO = UserInfoDto.builder().existsYn("N").build();
        }

        log.info("{}.checkEmail End!", this.getClass().getName());
        return rDTO;
    }

    // [POST] /user/verifyEmailCode - 이메일 인증번호 확인 (비동기 AJAX)
    // 세션에 저장된 코드와 사용자가 입력한 코드를 비교
    // 일치하면 emailVerified=true 를 세션에 저장하여 회원가입 가능 상태로 만듦
    @ResponseBody
    @PostMapping("/verifyEmailCode")
    public MsgDto verifyEmailCode(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.verifyEmailCode Start!", this.getClass().getName());

        String code        = CmmUtil.nvl(request.getParameter("code")); // 사용자가 입력한 인증번호
        String sessionCode = (String) session.getAttribute("emailCode"); // 세션에 저장된 인증번호
        LocalDateTime sentTime = (LocalDateTime) session.getAttribute("emailCodeTime"); // 인증번호 발송 시각

        int res;
        String msg;
        if (sentTime == null || LocalDateTime.now().isAfter(sentTime.plusMinutes(5))) {
            // 발송 시각이 없거나 5분 초과 시 만료 처리
            res = 0;
            msg = "인증번호가 만료되었습니다. 다시 요청해주세요.";
        } else if (sessionCode != null && sessionCode.equals(code)) {
            // 인증번호 일치: 이메일 인증 완료 플래그 세션에 저장
            session.setAttribute("emailVerified", true);
            res = 1;
            msg = "인증되었습니다.";
        } else {
            // 인증번호 불일치
            res = 0;
            msg = "인증번호가 틀립니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.verifyEmailCode End!", this.getClass().getName());
        return dto;
    }

    // [POST] /user/signup - 회원가입 처리 (비동기 AJAX)
    // 이메일 인증이 완료되지 않은 경우 회원가입을 차단
    // 성공 시 이메일 인증 관련 세션 데이터를 초기화
    @ResponseBody
    @PostMapping("/signup")
    public MsgDto signup(
            @RequestParam String userId,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam(required = false) MultipartFile profileImage, // 프로필 이미지 (선택사항)
            HttpSession session) throws Exception {

        log.info("{}.signup Start!", this.getClass().getName());

        String msg;
        int res;

        Boolean verified = (Boolean) session.getAttribute("emailVerified");
        if (verified == null || !verified) {
            return MsgDto.builder().result(0).msg("이메일 인증을 완료해주세요.").build(); // 이메일 미인증 시 가입 차단
        }

        log.info("userId : {}, username : {}, name : {}, email : {}", userId, username, name, email);

        // 프로필 이미지 S3 업로드 (선택사항, 실패해도 가입은 진행)
        String profileImageUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                profileImageUrl = s3UploadService.upload(profileImage.getBytes(), profileImage.getContentType(), "profiles"); // S3 profiles 폴더에 업로드
            } catch (Exception e) {
                log.warn("프로필 이미지 업로드 실패 — 기본 아바타 사용: {}", e.getMessage()); // 업로드 실패 시 기본 아바타 사용
            }
        }

        // 회원가입 요청 DTO 구성 (null 안전 처리 포함)
        UserInfoDto pDTO = UserInfoDto.builder()
                .userId(CmmUtil.nvl(userId))
                .username(CmmUtil.nvl(username))
                .password(CmmUtil.nvl(password))
                .name(CmmUtil.nvl(name))
                .email(CmmUtil.nvl(email))
                .profileImageUrl(profileImageUrl)
                .build();

        boolean success = userInfoService.signup(pDTO); // 회원가입 서비스 호출

        if (success) {
            // 가입 성공: 이메일 인증 관련 세션 데이터 초기화
            session.removeAttribute("emailCode");
            session.removeAttribute("emailTarget");
            session.removeAttribute("emailVerified");
            res = 1;
            msg = "회원가입되었습니다.";
        } else {
            res = 0;
            msg = "회원가입에 실패했습니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.signup End!", this.getClass().getName());
        return dto;
    }

    // [GET] /user/login - 로그인 화면 반환
    @GetMapping("/login")
    public String loginForm() {
        log.info("{}.user/login Start!", this.getClass().getName());
        log.info("{}.user/login End!", this.getClass().getName());
        return "user/login"; // templates/user/login.html 반환
    }

    // [POST] /user/loginProc - 로그인 처리 (비동기 AJAX)
    // 성공 시 세션에 SS_USER_ID(아이디) 와 SS_USER_NAME(이름) 을 저장
    // LoginInterceptor 는 SS_USER_ID 세션 값으로 로그인 여부를 판단
    @ResponseBody
    @PostMapping("/loginProc")
    public MsgDto loginProc(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.loginProc Start!", this.getClass().getName());

        String msg;
        String userId   = CmmUtil.nvl(request.getParameter("userId"));   // 입력한 아이디
        String password = CmmUtil.nvl(request.getParameter("password")); // 입력한 비밀번호

        log.info("userId : {}, password : {}", userId, password);

        UserInfoDto rDTO = userInfoService.login(userId, password); // 아이디 + 비밀번호 검증

        int res;
        if (rDTO != null) {
            // 로그인 성공: 세션에 사용자 정보 저장 (SS_ 접두사 = 세션 지속 데이터)
            session.setAttribute("SS_USER_ID",   userId);      // 로그인 아이디
            session.setAttribute("SS_USER_NAME", rDTO.name()); // 사용자 이름
            res = 1;
            msg = "로그인이 성공했습니다.";
        } else {
            // 로그인 실패: 아이디 또는 비밀번호 불일치
            res = 0;
            msg = "아이디와 비밀번호가 올바르지 않습니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.loginProc End!", this.getClass().getName());
        return dto;
    }

    // [GET] /user/service - 서비스 시작 화면 반환
    @GetMapping("/service")
    public String service() {
        return "user/service"; // templates/user/service.html 반환
    }

    // [POST] /user/logout - 로그아웃 처리
    // 세션을 무효화하고 브라우저의 JSESSIONID 쿠키를 만료시켜 완전히 로그아웃
    @PostMapping("/logout")
    public String logout(HttpSession session, HttpServletResponse response) {
        log.info("{}.logout Start!", this.getClass().getName());

        session.invalidate(); // 서버 세션 삭제

        // 브라우저의 JSESSIONID 쿠키를 명시적으로 만료시켜 클라이언트 세션도 제거
        Cookie sessionCookie = new Cookie("JSESSIONID", null);
        sessionCookie.setMaxAge(0); // 즉시 만료
        sessionCookie.setPath("/");
        response.addCookie(sessionCookie);

        log.info("{}.logout End!", this.getClass().getName());
        return "redirect:/"; // 메인 페이지로 리다이렉트
    }

    // [GET] /user/findId - 아이디 찾기 화면 반환
    @GetMapping("/findId")
    public String findIdForm() {
        log.info("{}.user/findId Start!", this.getClass().getName());
        log.info("{}.user/findId End!", this.getClass().getName());
        return "user/find-id"; // templates/user/find-id.html 반환
    }

    // [POST] /user/findId/sendCode - 아이디 찾기 인증번호 발송 (비동기 AJAX)
    // 이름과 이메일이 일치하는 유저가 있으면 인증번호를 이메일로 발송하고 세션에 저장
    @ResponseBody
    @PostMapping("/findId/sendCode")
    public MsgDto findIdSendCode(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findIdSendCode Start!", this.getClass().getName());

        String name  = CmmUtil.nvl(request.getParameter("name"));  // 입력한 이름
        String email = CmmUtil.nvl(request.getParameter("email")); // 입력한 이메일
        log.info("name : {}, email : {}", name, email);

        String code = userInfoService.findIdSendCode(name, email); // 이름+이메일로 유저 조회 후 인증번호 발송

        int res;
        String msg;
        if ("NOT_FOUND".equals(code)) {
            // 일치하는 유저 없음
            res = 0;
            msg = "아이디 혹은 이메일을 찾을수 없습니다.";
        } else {
            // 인증번호 발송 성공: 세션에 코드, 발송 시각, 이름, 이메일 저장
            session.setAttribute("findIdCode",     code);                // 발송된 인증번호
            session.setAttribute("findIdCodeTime", LocalDateTime.now()); // 발송 시각 (만료 시간 계산용)
            session.setAttribute("findIdName",     name);                // 입력한 이름
            session.setAttribute("findIdEmail",    email);               // 입력한 이메일
            res = 1;
            msg = "인증번호가 발송되었습니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.findIdSendCode End!", this.getClass().getName());
        return dto;
    }

    // [POST] /user/findId/verify - 아이디 찾기 인증번호 검증 후 아이디 반환 (비동기 AJAX)
    // 세션의 코드와 사용자 입력을 비교한 후 일치하면 userId 를 응답으로 전달
    @ResponseBody
    @PostMapping("/findId/verify")
    public UserInfoDto findIdVerify(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findIdVerify Start!", this.getClass().getName());

        String code            = CmmUtil.nvl(request.getParameter("code")); // 사용자가 입력한 인증번호
        String sessionCode     = (String) session.getAttribute("findIdCode");     // 세션에 저장된 인증번호
        LocalDateTime sentTime = (LocalDateTime) session.getAttribute("findIdCodeTime"); // 발송 시각
        String name            = (String) session.getAttribute("findIdName");  // 세션에 저장된 이름
        String email           = (String) session.getAttribute("findIdEmail"); // 세션에 저장된 이메일

        UserInfoDto rDTO;
        if (sentTime == null || LocalDateTime.now().isAfter(sentTime.plusMinutes(5))) {
            // 발송 시각이 없거나 5분 초과 시 만료 처리
            rDTO = UserInfoDto.builder().existsYn("EXPIRED").build();
        } else if (sessionCode != null && sessionCode.equals(code)) {
            // 인증번호 일치: 아이디 조회 후 반환
            String username = userInfoService.findUsername(name, email).orElse(""); // 이름+이메일로 아이디 조회
            session.removeAttribute("findIdCode");      // 인증번호 세션 초기화
            session.removeAttribute("findIdCodeTime");  // 발송 시각 세션 초기화
            rDTO = UserInfoDto.builder()
                    .userId(username)   // 찾은 아이디
                    .existsYn("Y")      // 인증 성공
                    .build();
        } else {
            // 인증번호 불일치
            rDTO = UserInfoDto.builder().existsYn("N").build();
        }

        log.info("{}.findIdVerify End!", this.getClass().getName());
        return rDTO;
    }

    // [GET] /user/findPassword - 비밀번호 찾기 화면 반환
    @GetMapping("/findPassword")
    public String findPasswordForm() {
        log.info("{}.user/findPassword Start!", this.getClass().getName());
        log.info("{}.user/findPassword End!", this.getClass().getName());
        return "user/find-password"; // templates/user/find-password.html 반환
    }

    // [POST] /user/findPassword/sendCode - 비밀번호 찾기 인증번호 발송 (비동기 AJAX)
    // 아이디와 이메일이 일치하는 유저가 있으면 인증번호를 이메일로 발송하고 세션에 저장
    // find-password.html 은 이메일 인증만으로 바로 비밀번호 변경 화면으로 넘어감
    @ResponseBody
    @PostMapping("/findPassword/sendCode")
    public MsgDto findPasswordSendCode(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findPasswordSendCode Start!", this.getClass().getName());

        String userId = CmmUtil.nvl(request.getParameter("userId")); // 입력한 아이디
        String email  = CmmUtil.nvl(request.getParameter("email"));  // 입력한 이메일
        log.info("userId : {}, email : {}", userId, email);

        String code = userInfoService.findPasswordSendCode(userId, email); // 아이디+이메일로 유저 조회 후 인증번호 발송

        int res;
        String msg;
        if ("NOT_FOUND".equals(code)) {
            // 일치하는 유저 없음
            res = 0;
            msg = "아이디 혹은 이메일을 찾을수 없습니다.";
        } else {
            // 인증번호 발송 성공: 세션에 코드, 발송 시각, 아이디 저장
            session.setAttribute("findPwCode",     code);                // 발송된 인증번호
            session.setAttribute("findPwCodeTime", LocalDateTime.now()); // 발송 시각 (만료 시간 계산용)
            session.setAttribute("findPwUserId",   userId);              // 비밀번호 변경 대상 아이디
            res = 1;
            msg = "인증번호가 발송되었습니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.findPasswordSendCode End!", this.getClass().getName());
        return dto;
    }

    // [POST] /user/findPassword/verify - 비밀번호 찾기 인증번호 검증 (비동기 AJAX)
    // 성공 시 findPwVerified=true 를 세션에 저장하여 비밀번호 변경 가능 상태로 만듦
    @ResponseBody
    @PostMapping("/findPassword/verify")
    public MsgDto findPasswordVerify(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findPasswordVerify Start!", this.getClass().getName());

        String code            = CmmUtil.nvl(request.getParameter("code")); // 사용자가 입력한 인증번호
        String sessionCode     = (String) session.getAttribute("findPwCode");     // 세션에 저장된 인증번호
        LocalDateTime sentTime = (LocalDateTime) session.getAttribute("findPwCodeTime"); // 발송 시각

        int res;
        String msg;
        if (sentTime == null || LocalDateTime.now().isAfter(sentTime.plusMinutes(5))) {
            // 발송 시각이 없거나 5분 초과 시 만료 처리
            res = 0;
            msg = "인증번호가 만료되었습니다. 다시 요청해주세요.";
        } else if (sessionCode != null && sessionCode.equals(code)) {
            // 인증번호 일치: 비밀번호 변경 가능 플래그 세션에 저장
            session.setAttribute("findPwVerified", true);
            res = 1;
            msg = "인증되었습니다.";
        } else {
            // 인증번호 불일치
            res = 0;
            msg = "인증번호가 틀립니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.findPasswordVerify End!", this.getClass().getName());
        return dto;
    }

    // [POST] /user/findPassword/change - 비밀번호 찾기 후 비밀번호 변경 (비동기 AJAX)
    // 세션에서 대상 userId 를 꺼내 비밀번호를 변경
    // 완료 후 비밀번호 찾기 관련 세션 데이터를 초기화
    @ResponseBody
    @PostMapping("/findPassword/change")
    public MsgDto changePasswordByFind(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.changePasswordByFind Start!", this.getClass().getName());

        String newPassword = CmmUtil.nvl(request.getParameter("newPassword")); // 새 비밀번호
        String userId      = (String) session.getAttribute("findPwUserId");    // 비밀번호 변경 대상 아이디

        boolean success = userInfoService.changePasswordByUsername(userId, newPassword); // 비밀번호 변경 서비스 호출

        int res;
        String msg;
        if (success) {
            // 변경 성공: 비밀번호 찾기 관련 세션 데이터 초기화
            session.removeAttribute("findPwCode");
            session.removeAttribute("findPwUserId");
            session.removeAttribute("findPwVerified");
            res = 1;
            msg = "비밀번호가 성공적으로 변경되었습니다.";
        } else {
            res = 0;
            msg = "비밀번호 변경에 실패했습니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.changePasswordByFind End!", this.getClass().getName());
        return dto;
    }

    // [GET] /user/mypage - 마이페이지 화면 반환
    // 유저 기본 정보(이름·아이디·이메일) + 독후감 이력(MongoDB) + 이미지 이력(MySQL) 을 Model 에 담아 전달
    @GetMapping("/mypage")
    public String mypage(HttpSession session, Model model) throws Exception {
        log.info("{}.user/mypage Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login"; // 비로그인 시 로그인 페이지로 이동

        UserInfoDto userInfo = userInfoService.getUserInfo(userId).orElse(null); // 유저 기본 정보 조회 (이메일 AES 복호화 포함)
        model.addAttribute("user", userInfo); // 템플릿에 유저 정보 전달

        List<BookReviewDocument> reviews = reviewService.getReviewsByUserId(userId); // 독후감 이력 조회 (MongoDB aireviews 컬렉션)
        model.addAttribute("reviews", reviews); // 템플릿에 독후감 이력 전달

        List<ImageResponseDto> images = imageService.getUserImages(userId); // AI 이미지 생성 이력 조회 (MySQL image_results 테이블, 최신순)
        model.addAttribute("images", images); // 템플릿에 이미지 이력 전달

        log.info("{}.user/mypage End!", this.getClass().getName());
        return "user/mypage"; // templates/user/mypage.html 반환
    }

    // [GET] /user/dashboard/stats - 대시보드 독서 통계 데이터를 JSON 으로 반환
    // 유저가 등록한 책 목록과 등록 날짜를 반환하여 대시보드 차트에 활용
    @GetMapping("/dashboard/stats")
    @ResponseBody
    public ResponseEntity<?> dashboardStats(HttpSession session) {
        log.info("{}.dashboardStats Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(MsgDto.builder().result(0).msg("로그인이 필요합니다.").build()); // 비로그인 시 401 반환

        List<BookSearchDto> books = bookService.findByUserId(userId); // 유저가 등록한 책 목록 조회 (createdAt = 등록 날짜, 날짜별 독서 기록으로 활용)

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // 날짜 포맷 "년-월-일" 형식으로 지정
        List<Map<String, String>> bookList = books.stream()
                .map(b -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("title",  b.title());  // 책 제목
                    m.put("author", b.author()); // 저자
                    m.put("date",   b.createdAt() != null
                            ? b.createdAt().format(fmt) : ""); // 등록 날짜 (없으면 빈 문자열)
                    return m;
                })
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("userName", session.getAttribute("SS_USER_NAME")); // 사용자 이름
        result.put("books",    bookList);                              // 책 목록

        log.info("{}.dashboardStats End! - {}권", this.getClass().getName(), bookList.size());
        return ResponseEntity.ok(result); // 통계 데이터 JSON 반환
    }

    // [GET] /user/mypage/reviews - 독후감 이력 화면 반환
    // MongoDB 에서 유저의 독후감 이력을 조회하여 Model 에 담아 전달
    @GetMapping("/mypage/reviews")
    public String mypageReviews(HttpSession session, Model model) {
        log.info("{}.mypageReviews Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID"); // 세션에서 로그인 사용자 ID 추출

        List<BookReviewDocument> reviews = reviewService.getReviewsByUserId(userId); // MongoDB 에서 유저의 독후감 이력 조회
        model.addAttribute("reviews", reviews); // 템플릿에 독후감 이력 전달

        log.info("{}.mypageReviews End!", this.getClass().getName());
        return "user/mypage-reviews"; // templates/user/mypage-reviews.html 반환
    }

    // [POST] /user/mypage/changePassword - 마이페이지 비밀번호 변경 (비동기 AJAX)
    // 현재 비밀번호 검증 후 새 비밀번호로 변경
    @ResponseBody
    @PostMapping("/mypage/changePassword")
    public MsgDto changePasswordMypage(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.changePasswordMypage Start!", this.getClass().getName());

        String currentPassword = CmmUtil.nvl(request.getParameter("currentPassword")); // 현재 비밀번호
        String newPassword     = CmmUtil.nvl(request.getParameter("newPassword"));     // 새 비밀번호
        String userId          = (String) session.getAttribute("SS_USER_ID");          // 로그인 사용자 ID

        UserInfoDto rDTO = userInfoService.login(userId, currentPassword); // login() 재사용하여 현재 비밀번호 일치 여부 확인

        int res;
        String msg;
        if (rDTO != null) {
            // 현재 비밀번호 일치: 새 비밀번호로 변경
            boolean success = userInfoService.changePassword(userId, newPassword);
            res = success ? 1 : 0;
            msg = success ? "비밀번호가                                                                                                                              변경되었습니다." : "비밀번호 변경에 실패했습니다.";
        } else {
            // 현재 비밀번호 불일치
            res = 0;
            msg = "현재 비밀번호가 일치하지 않습니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.changePasswordMypage End!", this.getClass().getName());
        return dto;
    }

    // [POST] /user/mypage/profile - 프로필 이미지 변경 (비동기 AJAX)
    // S3 에 새 이미지 업로드 후 DB 의 프로필 이미지 URL 업데이트
    // 성공 시 msg 필드에 새 이미지 URL 을 담아 반환 (JS 에서 바로 화면 반영)
    @ResponseBody
    @PostMapping("/mypage/profile")
    public MsgDto updateProfileImage(
            @RequestParam MultipartFile profileImage, // 새 프로필 이미지 파일
            HttpSession session) throws Exception {

        log.info("{}.updateProfileImage Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return MsgDto.builder().result(0).msg("로그인이 필요합니다.").build(); // 비로그인 시 에러 반환
        if (profileImage == null || profileImage.isEmpty())
            return MsgDto.builder().result(0).msg("이미지를 선택해주세요.").build(); // 파일이 없으면 에러 반환

        try {
            String url = s3UploadService.upload(profileImage.getBytes(), profileImage.getContentType(), "profiles"); // S3 profiles 폴더에 업로드
            userInfoService.updateProfileImage(userId, url); // DB 의 프로필 이미지 URL 업데이트
            log.info("{}.updateProfileImage End! - url:{}", this.getClass().getName(), url);
            return MsgDto.builder().result(1).msg(url).build(); // msg 에 새 이미지 URL 담아서 JS 에서 바로 반영
        } catch (Exception e) {
            log.error("프로필 이미지 변경 실패", e);
            return MsgDto.builder().result(0).msg("프로필 변경에 실패했습니다.").build();
        }
    }

    // [POST] /user/mypage/delete - 회원탈퇴 (비동기 AJAX)
    // 비밀번호 검증 후 계정 삭제, 성공 시 서버 세션 무효화
    @ResponseBody
    @PostMapping("/mypage/delete")
    public MsgDto deleteAccount(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.deleteAccount Start!", this.getClass().getName());

        String password = CmmUtil.nvl(request.getParameter("password")); // 입력한 비밀번호 (탈퇴 전 본인 확인용)
        String userId   = (String) session.getAttribute("SS_USER_ID");   // 로그인 사용자 ID

        boolean success = userInfoService.deleteUser(userId, password); // 비밀번호 검증 후 계정 삭제

        int res;
        String msg;
        if (success) {
            session.invalidate(); // 탈퇴 성공: 서버 세션 무효화 (쿠키 만료는 클라이언트에서 처리)
            res = 1;
            msg = "회원탈퇴가 완료되었습니다.";
        } else {
            // 비밀번호 불일치
            res = 0;
            msg = "비밀번호가 일치하지 않습니다.";
        }

        MsgDto dto = MsgDto.builder().result(res).msg(msg).build();

        log.info("{}.deleteAccount End!", this.getClass().getName());
        return dto;
    }
}