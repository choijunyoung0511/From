package com.from;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FROM 애플리케이션 진입점.
 *
 * @SpringBootApplication: @Configuration + @ComponentScan + @EnableAutoConfiguration 의 합성 어노테이션.
 *   - com.from 하위 패키지를 스캔하여 @Component, @Service, @Controller, @Repository 클래스를 자동 등록한다.
 *
 * @EnableScheduling: @Scheduled 어노테이션을 활성화한다.
 *   - RankingScheduler(매일 새벽 3시 랭킹 갱신)와 ReviewScheduler(1분마다 독후감 이메일 발송)가 동작한다.
 */
@SpringBootApplication
@EnableScheduling
public class FromApplication {

    public static void main(String[] args) {
        SpringApplication.run(FromApplication.class, args);
    }
}