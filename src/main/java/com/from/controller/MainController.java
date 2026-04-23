package com.from.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 페이지를 처리하는 컨트롤러.
 * 비로그인 사용자가 보호 페이지에 접근했을 때 리다이렉트되는 진입점이다.
 */
@Controller
@RequiredArgsConstructor
public class MainController {

    /**
     * 메인 페이지(/)를 반환한다.
     * LoginInterceptor가 비로그인 GET 요청을 차단하면 loginRequired=true를 세션에 저장하고 여기로 리다이렉트한다.
     * 이 메서드는 세션에서 loginRequired 플래그를 확인하여 Model에 담고, 화면에서 로그인 유도 메시지를 표시한다.
     */
    @GetMapping("/")
    public String index(HttpSession session, Model model) {

        // LoginInterceptor가 설정한 플래그: 비로그인 상태로 보호 페이지 접근 시 true
        if (session.getAttribute("loginRequired") != null) {
            model.addAttribute("loginRequired", true);
            // 한 번 읽고 나서 세션에서 제거 (새로고침 시 다시 표시되지 않도록)
            session.removeAttribute("loginRequired");
        }

        return "main/index";
    }
}