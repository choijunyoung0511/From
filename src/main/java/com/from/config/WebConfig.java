package com.from.config;

import com.from.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns(
                        "/book/**",      // 책 등록
                        "/ranking/**",   // 랭킹
                        "/review/**",    // 독후감
                        "/user/mypage",  // 마이페이지 (경로 수정)
                        "/service"       // 서비스 시작 화면 추가
                )
                .excludePathPatterns(
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