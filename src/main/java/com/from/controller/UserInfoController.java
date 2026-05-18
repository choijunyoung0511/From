package com.from.controller;

import com.from.domain.BookReviewDocument;
import com.from.dto.ImageResponseDto;
import com.from.dto.MsgDTO;
import com.from.dto.UserInfoDTO;
import com.from.dto.BookSearchDTO;
import com.from.service.IBookService;
import com.from.service.IImageService;
import com.from.service.IReviewService;
import com.from.service.IUserInfoService;
import com.from.util.CmmUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자 인증·계정·마이페이지·대시보드를 처리하는 컨트롤러.
 * /user/** 경로에 매핑되며, 인증이 필요한 경로는 LoginInterceptor가 자동으로 검사한다.
 *
 * @Slf4j: 롬복이 제공하는 로그 선언 어노테이션 (log.info(), log.error() 사용 가능)
 * @Controller: 이 클래스가 MVC 컨트롤러임을 Spring에게 알린다
 * @RequestMapping: 이 클래스의 모든 메서드 URL은 /user로 시작한다
 * @RequiredArgsConstructor: final 필드를 자동으로 생성자 주입한다
 */
@Slf4j
@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserInfoController {

    private final IUserInfoService userInfoService;
    private final IReviewService reviewService;
    private final IBookService bookService;
    private final IImageService imageService;

    /**
     * 회원가입 화면을 반환한다.
     * GET: URL에 데이터를 포함하지 않고 페이지만 요청하는 방식
     */
    @GetMapping("/signup")
    public String signupForm() {
        log.info("{}.user/signup Start!", this.getClass().getName());
        log.info("{}.user/signup End!", this.getClass().getName());
        return "user/signup";
    }

    /**
     * 아이디 중복 체크 (비동기 AJAX).
     * POST: 데이터를 HTTP 바디에 담아 전송하는 방식. GET보다 보안이 높다.
     * @ResponseBody: 반환값을 뷰 이름이 아닌 JSON으로 직접 응답한다.
     *
     * @return existsYn: "Y" = 이미 사용 중, "N" = 사용 가능
     */
    @ResponseBody
    @PostMapping("/checkUsername")
    public UserInfoDTO checkUsername(HttpServletRequest request) throws Exception {
        log.info("{}.checkUsername Start!", this.getClass().getName());

        // CmmUtil.nvl(): null이면 빈 문자열로 안전하게 처리
        String username = CmmUtil.nvl(request.getParameter("username"));
        log.info("username : {}", username);

        // 서비스 호출: 해당 아이디가 이미 존재하는지 확인("Y"/"N" 반환)
        String existsYn = userInfoService.checkUsernameExists(username);

        // 빌더 패턴으로 응답 DTO 생성 후 반환 (JSON으로 자동 직렬화)
        UserInfoDTO rDTO = UserInfoDTO.builder()
                .existsYn(existsYn)
                .build();

        log.info("{}.checkUsername End!", this.getClass().getName());
        return rDTO;
    }

    /**
     * 이메일 중복 체크 + 인증번호 발송 (비동기 AJAX).
     * 이미 가입된 이메일이면 existsYn="Y", 신규 이메일이면 인증번호를 발송하고 세션에 저장한다.
     */
    @ResponseBody
    @PostMapping("/checkEmail")
    public UserInfoDTO checkEmail(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.checkEmail Start!", this.getClass().getName());

        String email = CmmUtil.nvl(request.getParameter("email"));
        log.info("email : {}", email);

        String code = userInfoService.checkEmailAndSendCode(email);

        UserInfoDTO rDTO;
        if ("DUPLICATE".equals(code)) {
            // 중복 이메일: 이미 사용 중임을 알림
            rDTO = UserInfoDTO.builder().existsYn("Y").build();
        } else {
            // 인증번호 발송 성공: 세션에 인증번호, 발송 시각, 대상 이메일 저장
            session.setAttribute("emailCode",     code);
            session.setAttribute("emailCodeTime", LocalDateTime.now());
            session.setAttribute("emailTarget",   email);
            rDTO = UserInfoDTO.builder().existsYn("N").build();
        }

        log.info("{}.checkEmail End!", this.getClass().getName());
        return rDTO;
    }

    /**
     * 이메일 인증번호 확인 (비동기 AJAX).
     * 세션에 저장된 코드와 사용자가 입력한 코드를 비교한다.
     * 일치하면 emailVerified=true를 세션에 저장하여 회원가입 가능 상태로 만든다.
     */
    @ResponseBody
    @PostMapping("/verifyEmailCode")
    public MsgDTO verifyEmailCode(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.verifyEmailCode Start!", this.getClass().getName());

        String code        = CmmUtil.nvl(request.getParameter("code"));
        String sessionCode = (String) session.getAttribute("emailCode");
        LocalDateTime sentTime = (LocalDateTime) session.getAttribute("emailCodeTime");

        int res;
        String msg;
        if (sentTime == null || LocalDateTime.now().isAfter(sentTime.plusMinutes(5))) {
            res = 0;
            msg = "인증번호가 만료되었습니다. 다시 요청해주세요.";
        } else if (sessionCode != null && sessionCode.equals(code)) {
            session.setAttribute("emailVerified", true);
            res = 1;
            msg = "인증되었습니다.";
        } else {
            res = 0;
            msg = "인증번호가 틀립니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.verifyEmailCode End!", this.getClass().getName());
        return dto;
    }

    /**
     * 회원가입을 처리한다 (비동기 AJAX).
     * 이메일 인증이 완료되지 않은 경우 회원가입을 차단한다.
     * 성공 시 이메일 인증 관련 세션 데이터를 초기화한다.
     */
    @ResponseBody
    @PostMapping("/signup")
    public MsgDTO signup(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.signup Start!", this.getClass().getName());

        String msg;
        int res;

        // 이메일 인증 완료 여부 확인 (세션에 emailVerified=true가 없으면 차단)
        Boolean verified = (Boolean) session.getAttribute("emailVerified");
        if (verified == null || !verified) {
            MsgDTO dto = MsgDTO.builder().result(0).msg("이메일 인증을 완료해주세요.").build();
            return dto;
        }

        // 폼 데이터 추출
        String userId   = CmmUtil.nvl(request.getParameter("userId"));
        String username = CmmUtil.nvl(request.getParameter("username"));
        String password = CmmUtil.nvl(request.getParameter("password"));
        String name     = CmmUtil.nvl(request.getParameter("name"));
        String email    = CmmUtil.nvl(request.getParameter("email"));

        log.info("userId : {}, username : {}, name : {}, email : {}", userId, username, name, email);

        // 파라미터를 DTO에 담아 서비스로 전달
        UserInfoDTO pDTO = UserInfoDTO.builder()
                .userId(userId)
                .username(username)
                .password(password)
                .name(name)
                .email(email)
                .build();

        boolean success = userInfoService.signup(pDTO);

        if (success) {
            // 가입 성공: 인증 관련 세션 데이터 초기화
            session.removeAttribute("emailCode");
            session.removeAttribute("emailTarget");
            session.removeAttribute("emailVerified");
            res = 1;
            msg = "회원가입되었습니다.";
        } else {
            res = 0;
            msg = "회원가입에 실패했습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.signup End!", this.getClass().getName());
        return dto;
    }

    /**
     * 로그인 화면을 반환한다.
     */
    @GetMapping("/login")
    public String loginForm() {
        log.info("{}.user/login Start!", this.getClass().getName());
        log.info("{}.user/login End!", this.getClass().getName());
        return "user/login";
    }

    /**
     * 로그인을 처리한다 (비동기 AJAX).
     * 성공 시 세션에 SS_USER_ID(아이디)와 SS_USER_NAME(이름)을 저장한다.
     * LoginInterceptor는 이 SS_USER_ID 세션 값으로 로그인 여부를 판단한다.
     */
    @ResponseBody
    @PostMapping("/loginProc")
    public MsgDTO loginProc(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.loginProc Start!", this.getClass().getName());

        String msg;
        String userId   = CmmUtil.nvl(request.getParameter("userId"));
        String password = CmmUtil.nvl(request.getParameter("password"));

        log.info("userId : {}, password : {}", userId, password);

        UserInfoDTO rDTO = userInfoService.login(userId, password);

        int res;
        if (rDTO != null) {
            // 로그인 성공: 세션에 사용자 정보 저장 (SS_ 접두사 = 세션 지속 데이터)
            session.setAttribute("SS_USER_ID",   userId);
            session.setAttribute("SS_USER_NAME", rDTO.name());
            res = 1;
            msg = "로그인이 성공했습니다.";
        } else {
            res = 0;
            msg = "아이디와 비밀번호가 올바르지 않습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.loginProc End!", this.getClass().getName());
        return dto;
    }

    /**
     * 서비스 시작 화면을 반환한다.
     * LoginInterceptor가 /user/service 경로를 보호하므로 여기서는 별도 세션 확인 불필요.
     */
    @GetMapping("/service")
    public String service() {
        return "user/service";
    }

    /**
     * 로그아웃을 처리한다.
     * 세션을 무효화하고 브라우저의 JSESSIONID 쿠키를 만료시켜 완전히 로그아웃한다.
     */
    @PostMapping("/logout")
    public String logout(HttpSession session, HttpServletResponse response) {
        log.info("{}.logout Start!", this.getClass().getName());

        session.invalidate(); // 서버 세션 삭제

        // 브라우저의 JSESSIONID 쿠키를 명시적으로 만료시켜 클라이언트 세션도 제거
        Cookie sessionCookie = new Cookie("JSESSIONID", null);
        sessionCookie.setMaxAge(0);   // 즉시 만료
        sessionCookie.setPath("/");
        response.addCookie(sessionCookie);

        log.info("{}.logout End!", this.getClass().getName());
        return "redirect:/";
    }

    /**
     * 아이디 찾기 화면을 반환한다.
     */
    @GetMapping("/findId")
    public String findIdForm() {
        log.info("{}.user/findId Start!", this.getClass().getName());
        log.info("{}.user/findId End!", this.getClass().getName());
        return "user/find-id";
    }

    /**
     * 아이디 찾기 - 인증번호를 발송한다 (비동기 AJAX).
     * 이름과 이메일이 일치하는 유저가 있으면 인증번호를 이메일로 발송하고 세션에 저장한다.
     */
    @ResponseBody
    @PostMapping("/findId/sendCode")
    public MsgDTO findIdSendCode(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findIdSendCode Start!", this.getClass().getName());

        String name  = CmmUtil.nvl(request.getParameter("name"));
        String email = CmmUtil.nvl(request.getParameter("email"));

        log.info("name : {}, email : {}", name, email);

        String code = userInfoService.findIdSendCode(name, email);

        int res;
        String msg;
        if ("NOT_FOUND".equals(code)) {
            res = 0;
            msg = "아이디 혹은 이메일을 찾을수 없습니다.";
        } else {
            session.setAttribute("findIdCode",     code);
            session.setAttribute("findIdCodeTime", LocalDateTime.now());
            session.setAttribute("findIdName",     name);
            session.setAttribute("findIdEmail",    email);
            res = 1;
            msg = "인증번호가 발송되었습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.findIdSendCode End!", this.getClass().getName());
        return dto;
    }

    /**
     * 아이디 찾기 - 인증번호를 검증하고 아이디를 반환한다 (비동기 AJAX).
     * 세션의 코드와 사용자 입력을 비교한 후, 일치하면 userId를 응답으로 전달한다.
     */
    @ResponseBody
    @PostMapping("/findId/verify")
    public UserInfoDTO findIdVerify(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findIdVerify Start!", this.getClass().getName());

        String code        = CmmUtil.nvl(request.getParameter("code"));
        String sessionCode = (String) session.getAttribute("findIdCode");
        LocalDateTime sentTime = (LocalDateTime) session.getAttribute("findIdCodeTime");
        String name        = (String) session.getAttribute("findIdName");
        String email       = (String) session.getAttribute("findIdEmail");

        UserInfoDTO rDTO;
        if (sentTime == null || LocalDateTime.now().isAfter(sentTime.plusMinutes(5))) {
            rDTO = UserInfoDTO.builder().existsYn("EXPIRED").build();
        } else if (sessionCode != null && sessionCode.equals(code)) {
            String username = userInfoService.findUsername(name, email).orElse("");
            session.removeAttribute("findIdCode");
            session.removeAttribute("findIdCodeTime");
            rDTO = UserInfoDTO.builder()
                    .userId(username)
                    .existsYn("Y")
                    .build();
        } else {
            rDTO = UserInfoDTO.builder()
                    .existsYn("N")
                    .build();
        }

        log.info("{}.findIdVerify End!", this.getClass().getName());
        return rDTO;
    }

    /**
     * 비밀번호 찾기 화면을 반환한다.
     */
    @GetMapping("/findPassword")
    public String findPasswordForm() {
        log.info("{}.user/findPassword Start!", this.getClass().getName());
        log.info("{}.user/findPassword End!", this.getClass().getName());
        return "user/find-password";
    }

    /**
     * 비밀번호 찾기 - 인증번호를 발송한다 (비동기 AJAX).
     * 아이디와 이메일이 일치하는 유저가 있으면 인증번호를 이메일로 발송하고 세션에 저장한다.
     * find-password.html은 이메일 인증만으로 바로 비밀번호 변경 화면으로 넘어간다.
     */
    @ResponseBody
    @PostMapping("/findPassword/sendCode")
    public MsgDTO findPasswordSendCode(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findPasswordSendCode Start!", this.getClass().getName());

        String userId = CmmUtil.nvl(request.getParameter("userId"));
        String email  = CmmUtil.nvl(request.getParameter("email"));

        log.info("userId : {}, email : {}", userId, email);

        String code = userInfoService.findPasswordSendCode(userId, email);

        int res;
        String msg;
        if ("NOT_FOUND".equals(code)) {
            res = 0;
            msg = "아이디 혹은 이메일을 찾을수 없습니다.";
        } else {
            session.setAttribute("findPwCode",     code);
            session.setAttribute("findPwCodeTime", LocalDateTime.now());
            session.setAttribute("findPwUserId",   userId);
            res = 1;
            msg = "인증번호가 발송되었습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.findPasswordSendCode End!", this.getClass().getName());
        return dto;
    }

    /**
     * 비밀번호 찾기 - 인증번호를 검증한다 (비동기 AJAX).
     * 성공 시 findPwVerified=true를 세션에 저장하여 비밀번호 변경 가능 상태로 만든다.
     */
    @ResponseBody
    @PostMapping("/findPassword/verify")
    public MsgDTO findPasswordVerify(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findPasswordVerify Start!", this.getClass().getName());

        String code        = CmmUtil.nvl(request.getParameter("code"));
        String sessionCode = (String) session.getAttribute("findPwCode");
        LocalDateTime sentTime = (LocalDateTime) session.getAttribute("findPwCodeTime");

        int res;
        String msg;
        if (sentTime == null || LocalDateTime.now().isAfter(sentTime.plusMinutes(5))) {
            res = 0;
            msg = "인증번호가 만료되었습니다. 다시 요청해주세요.";
        } else if (sessionCode != null && sessionCode.equals(code)) {
            session.setAttribute("findPwVerified", true);
            res = 1;
            msg = "인증되었습니다.";
        } else {
            res = 0;
            msg = "인증번호가 틀립니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.findPasswordVerify End!", this.getClass().getName());
        return dto;
    }

    /**
     * 비밀번호 찾기 - 비밀번호를 변경한다 (비동기 AJAX).
     * 세션에서 대상 userId를 꺼내 비밀번호를 변경한다.
     * 완료 후 비밀번호 찾기 관련 세션 데이터를 초기화한다.
     */
    @ResponseBody
    @PostMapping("/findPassword/change")
    public MsgDTO changePasswordByFind(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.changePasswordByFind Start!", this.getClass().getName());

        String newPassword = CmmUtil.nvl(request.getParameter("newPassword"));
        String userId      = (String) session.getAttribute("findPwUserId");

        boolean success = userInfoService.changePasswordByUsername(userId, newPassword);

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

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.changePasswordByFind End!", this.getClass().getName());
        return dto;
    }

    /**
     * 마이페이지 화면을 반환한다.
     * 유저 기본 정보(이름·아이디·이메일)와 독후감 이력(MongoDB)을 Model에 담아 전달한다.
     */
    @GetMapping("/mypage")
    public String mypage(HttpSession session, Model model) throws Exception {
        log.info("{}.user/mypage Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        // 유저 기본 정보 조회 (이메일 AES 복호화 포함)
        UserInfoDTO userInfo = userInfoService.getUserInfo(userId).orElse(null);
        model.addAttribute("user", userInfo);

        // 독후감 이력 조회 (MongoDB aireviews 컬렉션)
        List<BookReviewDocument> reviews = reviewService.getReviewsByUserId(userId);
        model.addAttribute("reviews", reviews);

        // AI 이미지 생성 이력 조회 (MySQL imge_results 테이블, 최신순)
        List<ImageResponseDto> images = imageService.getUserImages(userId);
        model.addAttribute("images", images);

        log.info("{}.user/mypage End!", this.getClass().getName());
        return "user/mypage";
    }

    /**
     * 독서 대시보드 화면을 반환한다.
     * 유저 이름을 Model에 담아 대시보드 제목에 표시한다.
     * 실제 통계 데이터는 dashboardStats() AJAX API로 별도 요청한다.
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) throws Exception {
        log.info("{}.dashboard Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        model.addAttribute("userName", session.getAttribute("SS_USER_NAME"));

        log.info("{}.dashboard End!", this.getClass().getName());
        return "user/dashboard";
    }

    /**
     * 대시보드 독서 통계 데이터를 JSON으로 반환한다 (비동기 AJAX).
     * dashboard.html의 Chart.js가 이 API를 호출하여 그래프를 그린다.
     *
     * @return {userName, books: [{title, author, date}]} 형태의 통계 데이터
     */
    @GetMapping("/dashboard/stats")
    @ResponseBody
    public ResponseEntity<?> dashboardStats(HttpSession session) {
        log.info("{}.dashboardStats Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(MsgDTO.builder().result(0).msg("로그인이 필요합니다.").build());

        // 유저가 등록한 책 목록 조회 (createdAt = 등록 날짜, 대시보드의 날짜별 독서 기록으로 활용)
        List<BookSearchDTO> books = bookService.findByUserId(userId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<Map<String, String>> bookList = books.stream()
                .map(b -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("title",  b.title());
                    m.put("author", b.author());
                    m.put("date",   b.createdAt() != null
                            ? b.createdAt().format(fmt) : "");
                    return m;
                })
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("userName", session.getAttribute("SS_USER_NAME"));
        result.put("books",    bookList);

        log.info("{}.dashboardStats End! - {}권", this.getClass().getName(), bookList.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 마이페이지 - 독후감 이력 화면을 반환한다.
     * MongoDB에서 현재 유저의 독후감 목록을 조회하여 Model에 담는다.
     */
    @GetMapping("/mypage/reviews")
    public String mypageReviews(HttpSession session, Model model) {
        log.info("{}.mypageReviews Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");

        // MongoDB에서 유저의 독후감 이력 조회
        List<BookReviewDocument> reviews = reviewService.getReviewsByUserId(userId);
        model.addAttribute("reviews", reviews);

        log.info("{}.mypageReviews End!", this.getClass().getName());
        return "user/mypage-reviews";
    }

    /**
     * 마이페이지 - 비밀번호를 변경한다 (비동기 AJAX).
     * 현재 비밀번호를 먼저 검증(로그인 시도)한 후 새 비밀번호로 변경한다.
     */
    @ResponseBody
    @PostMapping("/mypage/changePassword")
    public MsgDTO changePasswordMypage(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.changePasswordMypage Start!", this.getClass().getName());

        String currentPassword = CmmUtil.nvl(request.getParameter("currentPassword"));
        String newPassword     = CmmUtil.nvl(request.getParameter("newPassword"));
        String userId          = (String) session.getAttribute("SS_USER_ID");

        // 현재 비밀번호 검증: login()을 재사용하여 비밀번호 일치 여부 확인
        UserInfoDTO rDTO = userInfoService.login(userId, currentPassword);

        int res;
        String msg;
        if (rDTO != null) {
            // 현재 비밀번호 일치: 새 비밀번호로 변경
            boolean success = userInfoService.changePassword(userId, newPassword);
            res = success ? 1 : 0;
            msg = success ? "비밀번호가 변경되었습니다." : "비밀번호 변경에 실패했습니다.";
        } else {
            res = 0;
            msg = "현재 비밀번호가 일치하지 않습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.changePasswordMypage End!", this.getClass().getName());
        return dto;
    }

    /**
     * 회원 탈퇴를 처리한다 (비동기 AJAX).
     * 비밀번호를 확인한 후 DB에서 유저를 삭제하고 세션을 무효화한다.
     */
    @ResponseBody
    @PostMapping("/mypage/delete")
    public MsgDTO deleteAccount(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.deleteAccount Start!", this.getClass().getName());

        String password = CmmUtil.nvl(request.getParameter("password"));
        String userId   = (String) session.getAttribute("SS_USER_ID");

        boolean success = userInfoService.deleteUser(userId, password);

        int res;
        String msg;
        if (success) {
            // 탈퇴 성공: 서버 세션 무효화 (쿠키 만료는 클라이언트에서 처리)
            session.invalidate();
            res = 1;
            msg = "회원탈퇴가 완료되었습니다.";
        } else {
            res = 0;
            msg = "비밀번호가 일치하지 않습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.deleteAccount End!", this.getClass().getName());
        return dto;
    }
}