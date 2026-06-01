// MainController.java - 메인 페이지를 처리하는 컨트롤러
// 비로그인 사용자가 보호 페이지에 접근했을 때 리다이렉트되는 진입점
package com.from.controller;

import jakarta.servlet.http.HttpSession; // HTTP 80 세션 (로그인 상태 확인용)
import lombok.RequiredArgsConstructor;   // final 필드 기반 생성자 자동 생성
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;             // 템플릿에 데이터 전달용
import org.springframework.web.bind.annotation.GetMapping;

@Controller          // Spring MVC 컨트롤러로 등록 (View 이름 반환 가능)
@RequiredArgsConstructor // final 필드를 인자로 받는 생성자 자동 생성 (의존성 주입)
public class MainController {

    // [GET] / - 메인 페이지 반환
    // LoginInterceptor 가 비로그인 GET 요청을 차단하면 loginRequired=true 를 세션에 저장하고 여기로 리다이렉트
    // 세션에서 loginRequired 플래그를 확인하여 Model 에 담고, 화면에서 로그인 유도 메시지를 표시
    @GetMapping("/")
    public String index(HttpSession session, Model model) {

        if (session.getAttribute("loginRequired") != null) { // LoginInterceptor 가 설정한 플래그: 비로그인 상태로 보호 페이지 접근 시 true
            model.addAttribute("loginRequired", true);        // 템플릿에 로그인 유도 메시지 표시 플래그 전달
            session.removeAttribute("loginRequired");         // 한 번 읽은 후 세션에서 제거 (새로고침 시 다시 표시되지 않도록)
        }

        return "main/index"; // templates/main/index.html 반환
    }
}