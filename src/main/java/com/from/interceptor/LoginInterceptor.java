package com.from.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 여부를 검사하는 인터셉터.
 * WebConfig에 등록된 보호 경로에 접근할 때 Controller 실행 전에 자동으로 호출된다.
 *
 * 비로그인 + GET 요청: 메인 페이지(/)로 리다이렉트하고 loginRequired 플래그를 세션에 저장
 * 비로그인 + POST/AJAX 요청: HTTP 401 + JSON 오류 응답 {"result":0,"msg":"로그인이 필요합니다."}
 * 로그인 완료: true를 반환하여 요청을 Controller로 통과시킴
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * Controller 실행 전에 호출되는 메서드.
     * true를 반환하면 다음 단계(Controller)로 진행하고,
     * false를 반환하면 요청 처리를 중단한다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession();

        // 세션에 SS_USER_ID가 없으면 비로그인 상태
        if (session.getAttribute("SS_USER_ID") == null) {
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                // AJAX POST 요청: JSON 형식으로 오류 응답
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // HTTP 401
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"result\":0,\"msg\":\"로그인이 필요합니다.\"}");
            } else {
                // 일반 GET 페이지 요청: 메인으로 리다이렉트
                // loginRequired 플래그를 세션에 저장 → 메인 페이지에서 로그인 유도 메시지 표시
                session.setAttribute("loginRequired", true);
                response.sendRedirect("/");
            }
            return false; // 요청 처리 중단
        }

        // 로그아웃 후 브라우저 뒤로 가기로 보호 페이지가 캐시에서 복원되는 것을 방지
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return true; // 로그인 확인 완료, Controller로 진행
    }
}