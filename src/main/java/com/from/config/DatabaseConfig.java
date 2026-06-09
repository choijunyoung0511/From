package com.from.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;


//MySQL 데이터베이스 연결 설정 클래스
@Configuration
public class DatabaseConfig {
    //properties 값 가져옴
    @Value("${spring.datasource.url}")
    private String url;


    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    /** JDBC 드라이버 클래스명 (com.mysql.cj.jdbc.Driver) */
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    //데이터소스 빈 등록

    @Bean
    @Primary
    //DB연결 객체를 관리하는 관리자,미리 연결을 여러개 만들어 놓고 재사용함=커넥션풀
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }
}