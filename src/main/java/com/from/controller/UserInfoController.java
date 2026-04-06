package com.from.controller;

import com.from.domain.Book;
import com.from.domain.BookReviewDocument;
import com.from.dto.MsgDTO;
import com.from.dto.UserInfoDTO;
import com.from.mapper.BookMapper;
import com.from.repository.BookReviewMongoRepository;
import com.from.service.IUserInfoService;
import com.from.util.CmmUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j //로그 찍는 어노테이션
@Controller // 이 클래스가 컨트롤러임을 스프링에게 암시
@RequestMapping("/user") //이클래스의 모든 url은 /user로 시작
@RequiredArgsConstructor //final 필드를 자동으로 생성자 주입
public class UserInfoController {
    /** 유저 비즈니스 로직 (실제 구현체: UserInfoService) */
    private final IUserInfoService userInfoService;
    /** MongoDB 독후감 저장소 (마이페이지 독후감 이력 조회에 사용) */
    private final BookReviewMongoRepository bookReviewMongoRepository;
    /** 책 MyBatis 매퍼 (대시보드 독서 통계에 사용) */
    private final BookMapper bookMapper;

    // 회원가입 화면
    @GetMapping("/signup")
    public String signupForm() {
        log.info("{}.user/signup Start!", this.getClass().getName());
        log.info("{}.user/signup End!", this.getClass().getName());
        return "user/signup";
    }

    // 아이디 중복 체크 (Ajax)
    @ResponseBody
    @PostMapping("/checkUsername")
    public UserInfoDTO checkUsername(HttpServletRequest request) throws Exception {
        log.info("{}.checkUsername Start!", this.getClass().getName());

        String username = CmmUtil.nvl(request.getParameter("username"));
        log.info("username : {}", username);

        String existsYn = userInfoService.checkUsernameExists(username);

        UserInfoDTO rDTO = UserInfoDTO.builder()
                .existsYn(existsYn)
                .build();

        log.info("{}.checkUsername End!", this.getClass().getName());
        return rDTO;
    }

    // 이메일 중복 체크 + 인증번호 발송 (Ajax)
    @ResponseBody
    @PostMapping("/checkEmail")
    public UserInfoDTO checkEmail(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.checkEmail Start!", this.getClass().getName());

        String email = CmmUtil.nvl(request.getParameter("email"));
        log.info("email : {}", email);

        String code = userInfoService.checkEmailAndSendCode(email);

        UserInfoDTO rDTO;
        //중복메일 일시 반환
        if ("DUPLICATE".equals(code)) {
            rDTO = UserInfoDTO.builder().existsYn("Y").build();
        } else {
            //발송 성공시 인증번호와 이메일을 세션에 저장
            session.setAttribute("emailCode", code); //인증번호
            session.setAttribute("emailTarget", email); //대상 이메일 저장
            rDTO = UserInfoDTO.builder().existsYn("N").build();
        }

        log.info("{}.checkEmail End!", this.getClass().getName());
        return rDTO;
    }

    // 이메일 인증번호 확인 (Ajax)
    @ResponseBody
    @PostMapping("/verifyEmailCode")
    public MsgDTO verifyEmailCode(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.verifyEmailCode Start!", this.getClass().getName());

        String code = CmmUtil.nvl(request.getParameter("code"));
        String sessionCode = (String) session.getAttribute("emailCode");

        int res;
        String msg;
        if (sessionCode != null && sessionCode.equals(code)) {
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

    // 회원가입 처리 (Ajax)
    @ResponseBody
    @PostMapping("/signup")
    public MsgDTO signup(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.signup Start!", this.getClass().getName());

        String msg;
        int res;

        Boolean verified = (Boolean) session.getAttribute("emailVerified");
        if (verified == null || !verified) {
            MsgDTO dto = MsgDTO.builder().result(0).msg("이메일 인증을 완료해주세요.").build();
            return dto;
        }

        String userId   = CmmUtil.nvl(request.getParameter("userId"));
        String username = CmmUtil.nvl(request.getParameter("username"));
        String password = CmmUtil.nvl(request.getParameter("password"));
        String name     = CmmUtil.nvl(request.getParameter("name"));
        String email    = CmmUtil.nvl(request.getParameter("email"));

        log.info("userId : {}, username : {}, name : {}, email : {}", userId, username, name, email);

        UserInfoDTO pDTO = UserInfoDTO.builder()
                .userId(userId)
                .username(username)
                .password(password)
                .name(name)
                .email(email)
                .build();

        boolean success = userInfoService.signup(pDTO);

        if (success) {
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

    // 로그인 화면
    @GetMapping("/login")
    public String loginForm() {
        log.info("{}.user/login Start!", this.getClass().getName());
        log.info("{}.user/login End!", this.getClass().getName());
        return "user/login";
    }

    // 로그인 처리 (Ajax)
    @ResponseBody
    @PostMapping("/loginProc")
    public MsgDTO loginProc(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.loginProc Start!", this.getClass().getName());

        String msg;

        String userId   = CmmUtil.nvl(request.getParameter("userId"));
        String password = CmmUtil.nvl(request.getParameter("password"));

        log.info("userId : {}, password : {}", userId, password);

        UserInfoDTO pDTO = UserInfoDTO.builder()
                .userId(userId)
                .password(password)
                .build();

        UserInfoDTO rDTO = userInfoService.login(userId, password);

        int res;
        if (rDTO != null) {
            session.setAttribute("SS_USER_ID", userId);
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



    // 서비스 시작
    @GetMapping("/service")
    public String service(HttpSession session) {
        if (session.getAttribute("SS_USER_ID") == null) {
            return "redirect:/user/login";
        }
        return "user/service";
    }

    // 로그아웃
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        log.info("{}.logout Start!", this.getClass().getName());
        session.invalidate();
        log.info("{}.logout End!", this.getClass().getName());
        return "redirect:/"; // ← /main/index → /
    }

    // 아이디 찾기 화면
    @GetMapping("/findId")
    public String findIdForm() {
        log.info("{}.user/findId Start!", this.getClass().getName());
        log.info("{}.user/findId End!", this.getClass().getName());
        return "user/find-id";
    }

    // 아이디 찾기 - 인증번호 발송 (Ajax)
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
            session.setAttribute("findIdCode", code);
            session.setAttribute("findIdName", name);
            session.setAttribute("findIdEmail", email);
            res = 1;
            msg = "인증번호가 발송되었습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.findIdSendCode End!", this.getClass().getName());
        return dto;
    }

    // 아이디 찾기 - 인증번호 확인 (Ajax)
    @ResponseBody
    @PostMapping("/findId/verify")
    public UserInfoDTO findIdVerify(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findIdVerify Start!", this.getClass().getName());

        String code  = CmmUtil.nvl(request.getParameter("code"));
        String sessionCode = (String) session.getAttribute("findIdCode");
        String name  = (String) session.getAttribute("findIdName");
        String email = (String) session.getAttribute("findIdEmail");

        UserInfoDTO rDTO;
        if (sessionCode != null && sessionCode.equals(code)) {
            String username = userInfoService.findUsername(name, email);
            session.removeAttribute("findIdCode");
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

    // 비밀번호 찾기 화면
    @GetMapping("/findPassword")
    public String findPasswordForm() {
        log.info("{}.user/findPassword Start!", this.getClass().getName());
        log.info("{}.user/findPassword End!", this.getClass().getName());
        return "user/find-password";
    }

    // 비밀번호 찾기 - 인증번호 발송 (Ajax)
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
            session.setAttribute("findPwCode", code);
            session.setAttribute("findPwUserId", userId);
            res = 1;
            msg = "인증번호가 발송되었습니다.";
        }

        MsgDTO dto = MsgDTO.builder().result(res).msg(msg).build();

        log.info("{}.findPasswordSendCode End!", this.getClass().getName());
        return dto;
    }

    // 비밀번호 찾기 - 인증번호 확인 (Ajax)
    @ResponseBody
    @PostMapping("/findPassword/verify")
    public MsgDTO findPasswordVerify(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.findPasswordVerify Start!", this.getClass().getName());

        String code = CmmUtil.nvl(request.getParameter("code"));
        String sessionCode = (String) session.getAttribute("findPwCode");

        int res;
        String msg;
        if (sessionCode != null && sessionCode.equals(code)) {
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

    // 비밀번호 변경 - 비밀번호 찾기용 (Ajax)
    @ResponseBody
    @PostMapping("/findPassword/change")
    public MsgDTO changePasswordByFind(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.changePasswordByFind Start!", this.getClass().getName());

        String newPassword = CmmUtil.nvl(request.getParameter("newPassword"));
        String userId = (String) session.getAttribute("findPwUserId");

        boolean success = userInfoService.changePasswordByUsername(userId, newPassword);

        int res;
        String msg;
        if (success) {
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
     * 마이페이지 화면.
     * 유저 정보(이름·아이디·이메일)와 독후감 이력을 Model 에 담아 반환한다.
     */
    @GetMapping("/mypage")
    public String mypage(HttpSession session, Model model) throws Exception {
        log.info("{}.user/mypage Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        // 유저 기본 정보 조회 (이메일 복호화 포함)
        UserInfoDTO userInfo = userInfoService.getUserInfo(userId);
        model.addAttribute("user", userInfo);

        // 독후감 이력 조회 (탭 2에 표시)
        List<BookReviewDocument> reviews = bookReviewMongoRepository.findByUserId(userId);
        model.addAttribute("reviews", reviews);

        log.info("{}.user/mypage End!", this.getClass().getName());
        return "user/mypage";
    }

    /**
     * 독서 대시보드 화면.
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) throws Exception {
        log.info("{}.dashboard Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        // 유저 이름을 Model 에 담아 대시보드 제목에 표시
        model.addAttribute("userName", session.getAttribute("SS_USER_NAME"));

        log.info("{}.dashboard End!", this.getClass().getName());
        return "user/dashboard";
    }

    /**
     * 대시보드 독서 통계 JSON API.
     * 유저가 등록한 책 목록을 [{title, author, date}] 형태로 반환한다.
     * dashboard.html 의 Chart.js 가 이 데이터를 AJAX 로 가져온다.
     */
    @GetMapping("/dashboard/stats")
    @ResponseBody
    public ResponseEntity<?> dashboardStats(HttpSession session) {
        log.info("{}.dashboardStats Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).build();

        // 등록된 책 목록 조회 (createdAt 을 독서 날짜로 사용)
        List<Book> books = bookMapper.findBooksByUserId(userId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<Map<String, String>> bookList = books.stream()
                .map(b -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("title",  b.getTitle());
                    m.put("author", b.getAuthor());
                    m.put("date",   b.getCreatedAt() != null
                            ? b.getCreatedAt().format(fmt) : "");
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
     * 마이페이지 - 내 독후감 이력 화면을 반환한다.
     * MongoDB 에서 현재 유저의 독후감 목록을 조회하여 Model 에 담는다.
     *
     * @param session 로그인 세션
     * @param model   독후감 목록(reviews) 전달용
     * @return user/mypage-reviews 템플릿
     */
    @GetMapping("/mypage/reviews")
    public String mypageReviews(HttpSession session, Model model) {
        log.info("{}.mypageReviews Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");

        // MongoDB 에서 유저의 독후감 이력 조회
        List<BookReviewDocument> reviews = bookReviewMongoRepository.findByUserId(userId);
        model.addAttribute("reviews", reviews);

        log.info("{}.mypageReviews End!", this.getClass().getName());
        return "user/mypage-reviews";
    }

    // 비밀번호 변경 - 마이페이지용 (Ajax)
    @ResponseBody
    @PostMapping("/mypage/changePassword")
    public MsgDTO changePasswordMypage(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.changePasswordMypage Start!", this.getClass().getName());

        String currentPassword = CmmUtil.nvl(request.getParameter("currentPassword"));
        String newPassword     = CmmUtil.nvl(request.getParameter("newPassword"));
        String userId = (String) session.getAttribute("SS_USER_ID");

        // 현재 비밀번호 검증 후 변경
        UserInfoDTO rDTO = userInfoService.login(userId, currentPassword);

        int res;
        String msg;
        if (rDTO != null) {
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

    // 회원 탈퇴 (Ajax)
    @ResponseBody
    @PostMapping("/mypage/delete")
    public MsgDTO deleteAccount(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("{}.deleteAccount Start!", this.getClass().getName());

        String password = CmmUtil.nvl(request.getParameter("password"));
        String userId = (String) session.getAttribute("SS_USER_ID");

        boolean success = userInfoService.deleteUser(userId, password);

        int res;
        String msg;
        if (success) {
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