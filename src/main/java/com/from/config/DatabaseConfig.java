package com.from.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * MySQL 데이터베이스 연결 설정 클래스.
 * application.properties에 정의된 DB 접속 정보를 읽어 HikariCP 커넥션 풀을 생성한다.
 *
 * HikariCP: 고성능 JDBC 커넥션 풀 라이브러리로, Spring Boot에 기본 내장되어 있다.
 * 커넥션 풀: DB 연결을 미리 만들어 두고 재사용하여 성능을 높이는 기법이다.
 */
@Configuration
public class DatabaseConfig {

    /** application.properties의 spring.datasource.url 값 주입 */
    @Value("${spring.datasource.url}")
    private String url;

    /** DB 사용자 이름 */
    @Value("${spring.datasource.username}")
    private String username;

    /** DB 비밀번호 */
    @Value("${spring.datasource.password}")
    private String password;

    /** JDBC 드라이버 클래스명 (com.mysql.cj.jdbc.Driver) */
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * MySQL DataSource Bean 등록.
     * @Primary: MongoDB DataSource와 충돌하지 않도록 JPA용 주 DataSource를 지정한다.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }
}