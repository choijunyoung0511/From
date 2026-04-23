package com.from.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB 연결 설정 클래스.
 * AI 독후감(aireviews 컬렉션)을 저장하는 MongoDB를 설정한다.
 *
 * @EnableMongoRepositories: com.from.repository 패키지에서 MongoRepository 구현체를 자동 생성한다.
 * @EnableMongoAuditing: @CreatedDate 등 자동 감사 어노테이션을 활성화한다.
 *                        BookReviewDocument.createdAt이 자동으로 설정된다.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.from.repository")
@EnableMongoAuditing
public class MongoConfig {

    /** application.properties의 spring.data.mongodb.uri 값 주입 */
    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    /**
     * MongoDB 클라이언트 Bean 등록.
     * URI를 파싱하여 MongoDB 서버(192.168.75.128:27017)에 연결한다.
     */
    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    /**
     * MongoTemplate Bean 등록.
     * "fromdb" 데이터베이스에 접근하는 핵심 MongoDB 조작 객체이다.
     * Spring Data MongoDB가 이를 기반으로 Repository 구현체를 자동 생성한다.
     */
    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), "fromdb");
    }
}