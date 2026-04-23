package com.from.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 애플리케이션 전역 Bean을 등록하는 설정 클래스.
 * @Configuration: 이 클래스가 Spring 설정 파일임을 선언한다.
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate Bean 등록.
     * NanobanaService(Gemini API 호출)에서 주입받아 HTTP 요청을 보낼 때 사용한다.
     * @Bean: 이 메서드가 반환하는 객체를 Spring이 관리하는 Bean으로 등록한다.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}