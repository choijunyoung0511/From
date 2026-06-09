package com.from.config;

import com.from.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


//스프링 MVC 설정 클래스
// 로그인 인터프리터를 등록하여 로그인이 필요한 경로를 보호한다
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns(
                        "/book/**",           // 책 검색·등록·AI 추천
                        "/ranking/**",        // 주간 랭킹
                        "/review/**",         // AI 독후감 생성
                        "/image/**",          // 이미지 생성 (Step 1~3)
                        "/user/mypage",       // 마이페이지
                        "/user/mypage/**",    // 마이페이지 하위 (독후감 이력, 통계 등)
                        "/user/dashboard",    // 독서 대시보드
                        "/user/dashboard/**", // 대시보드 통계
                        "/user/service"       // 서비스 시작 화면
                )
                .excludePathPatterns(
                        // 비로그인 사용자도 접근 가능한 공개 경로
                        "/",
                        "/user/login",
                        "/user/signup",
                        "/user/checkUsername",
                        "/user/checkEmail",
                        "/user/verifyEmailCode",
                        "/user/findId/**",
                        "/user/findPassword/**",
                        "/user/logout",
                        "/css/**",
                        "/js/**",
                        "/images/**"
                );
    }
}