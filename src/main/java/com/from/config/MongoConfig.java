package com.from.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

//몽고 연결
@Configuration
@EnableMongoRepositories(basePackages = "com.from.repository")
@EnableMongoAuditing
public class MongoConfig {

    //properties 값
    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    // 빈 등록 후 서버 연결
    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }


    //스프링 데이터 몽고가 이름 기반으로 레포지토리 구현체를 자동 생성한다.
    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), "fromdb");
    }
}