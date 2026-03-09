package com.from.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 여부 체크 인터셉터
 * - 비로그인 상태로 로그인 필요 페이지 접근 시 메인으로 redirect
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession();

        if (session.getAttribute("loginUser") == null) {
            // POST 요청이면 JSON 응답, GET 요청이면 redirect
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"로그인이 필요합니다.\"}");
            } else {
                session.setAttribute("loginRequired", true);
                response.sendRedirect("/");
            }
            return false;
        }

        return true;
    }
}