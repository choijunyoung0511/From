package com.from.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;


/*
 * [로그인-9] LoginInterceptor — 보호 경로 접근 시 세션 검사
 *
 * WebConfig 에서 /book/**, /ranking/**, /review/**, /user/mypage, /service 에 등록됨.
 * 컨트롤러 실행 전 preHandle()이 자동 호출되어 로그인 여부를 검사한다.
 *
 * 로그인 성공 후 브라우저에는 JSESSIONID 쿠키만 내려가고,
 * 실제 사용자 정보(SS_USER_ID, SS_USER_NAME)는 서버 세션에 저장된다.
 * 요청마다 쿠키로 세션을 조회하고 SS_USER_ID 존재 여부로 로그인 상태를 판단한다.
 */
public class LoginInterceptor implements HandlerInterceptor {

    // [로그인-9] true 반환 → 컨트롤러로 진행 / false 반환 → 요청 처리 중단
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession();

        // [로그인-9] 세션에 SS_USER_ID 없음 = 비로그인 상태
        if (session.getAttribute("SS_USER_ID") == null) {
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                // AJAX POST 요청: JSON 형식으로 401 오류 응답
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"result\":0,\"msg\":\"로그인이 필요합니다.\"}");
            } else {
                // 일반 GET 요청: 메인 페이지로 리다이렉트 (loginRequired 플래그로 안내 메시지 표시)
                session.setAttribute("loginRequired", true);
                response.sendRedirect("/");
            }
            return false; // 요청 처리 중단
        }

        // 로그아웃 후 브라우저 뒤로 가기로 보호 페이지가 캐시에서 복원되는 것을 방지
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return true; // [로그인-9] 로그인 확인 완료 → 컨트롤러로 진행
    }
}