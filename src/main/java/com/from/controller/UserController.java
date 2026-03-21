package com.from.controller;

import com.from.config.EncryptUtil;
import com.from.domain.BookReviewDocument;
import com.from.domain.User;
import com.from.dto.user.SessionUser;
import com.from.dto.user.SignupRequestDto;
import com.from.mapper.UserMapper;
import com.from.repository.BookReviewMongoRepository;
import com.from.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final BookReviewMongoRepository bookReviewMongoRepository;

    // 회원가입 화면
    @GetMapping("/signup")
    public String signupForm() { return "user/signup"; }

    // 아이디 중복 체크 (Ajax)
    @PostMapping("/checkUsername")
    @ResponseBody
    public Map<String, String> checkUsername(@RequestParam String username) {
        Map<String, String> result = new HashMap<>();
        result.put("existsYn", userService.checkUsernameExists(username));
        return result;
    }

    // 이메일 중복 체크 + 인증번호 발송 (Ajax)
    @PostMapping("/checkEmail")
    @ResponseBody
    public Map<String, String> checkEmail(@RequestParam String email, HttpSession session) {
        Map<String, String> result = new HashMap<>();
        String code = userService.checkEmailAndSendCode(email);

        if ("DUPLICATE".equals(code)) {
            result.put("existsYn", "Y");
        } else {
            result.put("existsYn", "N");
            session.setAttribute("emailCode", code);
            session.setAttribute("emailTarget", email);
        }
        return result;
    }

    // 이메일 인증번호 확인 (Ajax)
    @PostMapping("/verifyEmailCode")
    @ResponseBody
    public Map<String, Object> verifyEmailCode(@RequestParam String code, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String sessionCode = (String) session.getAttribute("emailCode");

        if (sessionCode != null && sessionCode.equals(code)) {
            result.put("success", true);
            session.setAttribute("emailVerified", true);
        } else {
            result.put("success", false);
        }
        return result;
    }

    // 회원가입 처리 (Ajax)
    @PostMapping("/signup")
    @ResponseBody
    public Map<String, Object> signup(@ModelAttribute SignupRequestDto dto, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        Boolean verified = (Boolean) session.getAttribute("emailVerified");
        if (verified == null || !verified) {
            result.put("success", false);
            result.put("message", "이메일 인증을 완료해주세요.");
            return result;
        }

        boolean success = userService.signup(dto);
        if (success) {
            session.removeAttribute("emailCode");
            session.removeAttribute("emailTarget");
            session.removeAttribute("emailVerified");
            result.put("success", true);
        } else {
            result.put("success", false);
            result.put("message", "회원가입에 실패했습니다.");
        }
        return result;
    }

    // 서비스 시작 화면
    @GetMapping("/service")
    public String servicePage(HttpSession session) {
        if (session.getAttribute("loginUser") == null) {
            return "redirect:/user/login";
        }
        return "service";
    }

    // 로그인 화면
    @GetMapping("/login")
    public String loginForm() { return "user/login"; }

    // 로그인 처리 (Ajax)
    @PostMapping("/login")
    @ResponseBody
    public Map<String, Object> login(@RequestParam String username,
                                     @RequestParam String password,
                                     HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        SessionUser sessionUser = userService.login(username, password);

        if (sessionUser != null) {
            session.setAttribute("loginUser", sessionUser);
            result.put("success", true);
        } else {
            result.put("success", false);
            result.put("message", "아이디 혹은 비밀번호가 틀렸습니다.");
        }
        return result;
    }

    // 로그아웃
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/?logout=true";
    }

    // 아이디 찾기 화면
    @GetMapping("/findId")
    public String findIdForm() { return "user/find-id"; }

    // 아이디 찾기 - 인증번호 발송 (Ajax)
    @PostMapping("/findId/sendCode")
    @ResponseBody
    public Map<String, Object> findIdSendCode(@RequestParam String name,
                                              @RequestParam String email,
                                              HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String code = userService.findIdSendCode(name, email);

        if ("NOT_FOUND".equals(code)) {
            result.put("success", false);
            result.put("message", "아이디 혹은 이메일을 찾을수 없습니다.");
        } else {
            session.setAttribute("findIdCode", code);
            session.setAttribute("findIdName", name);
            session.setAttribute("findIdEmail", email);
            result.put("success", true);
        }
        return result;
    }

    // 아이디 찾기 - 인증번호 확인 (Ajax)
    @PostMapping("/findId/verify")
    @ResponseBody
    public Map<String, Object> findIdVerify(@RequestParam String code, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String sessionCode = (String) session.getAttribute("findIdCode");
        String name  = (String) session.getAttribute("findIdName");
        String email = (String) session.getAttribute("findIdEmail");

        if (sessionCode != null && sessionCode.equals(code)) {
            String username = userService.findUsername(name, email);
            result.put("success", true);
            result.put("username", username);
            session.removeAttribute("findIdCode");
        } else {
            result.put("success", false);
            result.put("message", "인증번호가 틀립니다.");
        }
        return result;
    }

    // 비밀번호 찾기 화면
    @GetMapping("/findPassword")
    public String findPasswordForm() { return "user/find-password"; }

    // 비밀번호 찾기 - 인증번호 발송 (Ajax)
    @PostMapping("/findPassword/sendCode")
    @ResponseBody
    public Map<String, Object> findPasswordSendCode(@RequestParam String username,
                                                    @RequestParam String email,
                                                    HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String code = userService.findPasswordSendCode(username, email);

        if ("NOT_FOUND".equals(code)) {
            result.put("success", false);
            result.put("message", "아이디 혹은 이메일을 찾을수 없습니다.");
        } else {
            session.setAttribute("findPwCode", code);
            session.setAttribute("findPwUsername", username);
            result.put("success", true);
        }
        return result;
    }

    // 비밀번호 변경 (Ajax) - 비밀번호 찾기용
    @PostMapping("/findPassword/change")
    @ResponseBody
    public Map<String, Object> changePassword(@RequestParam String newPassword,
                                              HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String username = (String) session.getAttribute("findPwUsername");

        boolean success = userService.changePasswordByUsername(username, newPassword);
        if (success) {
            session.removeAttribute("findPwCode");
            session.removeAttribute("findPwUsername");
            result.put("success", true);
        } else {
            result.put("success", false);
            result.put("message", "비밀번호 변경에 실패했습니다.");
        }
        return result;
    }

    // ===== 마이페이지 =====
    @GetMapping("/mypage")
    public String mypage(HttpSession session) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";
        return "user/mypage";
    }

    // 내 독후감 기록 조회
    @GetMapping("/mypage/reviews")
    public String mypageReviews(HttpSession session, Model model) {
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        List<BookReviewDocument> reviews = bookReviewMongoRepository.findByUserId(loginUser.getUserId());
        model.addAttribute("reviews", reviews);
        return "user/mypage-reviews";
    }

    // 비밀번호 변경 페이지
    @GetMapping("/mypage/changePassword")
    public String changePasswordPage() {
        return "user/mypage-changePassword";
    }

    // 비밀번호 변경 처리 - 마이페이지용
    @PostMapping("/mypage/changePassword")
    @ResponseBody
    public Map<String, Object> changePasswordMypage(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        User user = userMapper.findByUserId(loginUser.getUserId());

        if (!user.getPassword().equals(EncryptUtil.encryptSHA256(currentPassword))) {
            result.put("success", false);
            result.put("message", "현재 비밀번호가 일치하지 않습니다.");
            return result;
        }

        userMapper.updatePasswordById(loginUser.getUserId(), EncryptUtil.encryptSHA256(newPassword));
        result.put("success", true);
        return result;
    }

    // 회원 탈퇴
    @PostMapping("/mypage/delete")
    @ResponseBody
    public Map<String, Object> deleteAccount(
            @RequestParam String password,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
        User user = userMapper.findByUserId(loginUser.getUserId());

        if (!user.getPassword().equals(EncryptUtil.encryptSHA256(password))) {
            result.put("success", false);
            return result;
        }

        userMapper.deleteUser(loginUser.getUserId());
        session.invalidate();
        result.put("success", true);
        return result;
    }
}