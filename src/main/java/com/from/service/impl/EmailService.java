package com.from.service.impl;

// 이메일 발송 서비스 인터페이스
import com.from.service.IEmailService;

// Java Mail 라이브러리 (이메일 메시지 객체)
import jakarta.mail.internet.MimeMessage;

// Lombok (생성자 자동 생성)
import lombok.RequiredArgsConstructor;

// Lombok (로그 객체 자동 생성)
import lombok.extern.slf4j.Slf4j;

// application.properties 값 주입
import org.springframework.beans.factory.annotation.Value;

// Spring Boot 이메일 발송 객체
import org.springframework.mail.javamail.JavaMailSender;

// 이메일 설정을 쉽게 해주는 헬퍼 클래스
import org.springframework.mail.javamail.MimeMessageHelper;

// Service Bean 등록
import org.springframework.stereotype.Service;


/**
 * EmailService
 *
 * 위치
 * Service 계층
 *
 * Controller → Service → SMTP 서버
 *
 * 역할
 * 이메일 인증번호를 실제로 발송하는 서비스
 *
 * 사용되는 곳
 * - 회원가입 이메일 인증
 * - 아이디 찾기 인증
 * - 비밀번호 찾기 인증
 *
 * 전체 흐름
 *
 * Controller
 *      ↓
 * EmailService.sendVerificationCode()
 *      ↓
 * JavaMailSender
 *      ↓
 * SMTP 서버
 *      ↓
 * 사용자 이메일
 */

@Slf4j // 로그 객체 생성 (log.info(), log.error())
@Service // Spring Bean 등록 → Controller에서 주입 가능
@RequiredArgsConstructor // final 필드 생성자 자동 생성
public class EmailService implements IEmailService {
    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String fromEmail;



    @Override
    public void sendVerificationCode(String toEmail, String code, String purpose) {

        try {

            // 이메일 제목 생성
            String subject = getSubject(purpose);

            // HTML 이메일 내용 생성
            String content = getContent(code, purpose);


            MimeMessage message = mailSender.createMimeMessage();


            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");


            // 발신 이메일 설정
            helper.setFrom(fromEmail);

            // 수신 이메일 설정
            helper.setTo(toEmail);

            // 이메일 제목 설정
            helper.setSubject(subject);

            // 이메일 내용 설정
            // true → HTML 이메일
            helper.setText(content, true);


            mailSender.send(message);


            // 이메일 발송 성공 로그
            log.info("이메일 발송 성공 - from: {}, to: {}, purpose: {}",
                    fromEmail, toEmail, purpose);

        } catch (Exception e) {

            // 이메일 발송 실패 로그
            log.error("이메일 발송 실패 - from: {}, to: {}, error: {}",
                    fromEmail, toEmail, e.getMessage());

            // Service 계층 예외 발생
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }


    private String getSubject(String purpose) {

        return switch (purpose) {

            // 회원가입 인증 메일
            case "SIGNUP" ->
                    "[FROM] 회원가입 이메일 인증번호";

            // 아이디 찾기 인증 메일
            case "FIND_ID" ->
                    "[FROM] 아이디 찾기 인증번호";

            // 비밀번호 찾기 인증 메일
            case "FIND_PASSWORD" ->
                    "[FROM] 비밀번호 찾기 인증번호";

            // 기본값
            default ->
                    "[FROM] 인증번호";
        };
    }


    private String getContent(String code, String purpose) {


        // 이메일 목적에 따라 제목 문구 변경
        String title = switch (purpose) {

            case "SIGNUP" ->
                    "회원가입 이메일 인증";

            case "FIND_ID" ->
                    "아이디 찾기 인증";

            case "FIND_PASSWORD" ->
                    "비밀번호 찾기 인증";

            default ->
                    "이메일 인증";
        };
        // css적용

        return """
                <div style="font-family: 'Nanum Myeongjo', serif; max-width: 500px; margin: 0 auto; padding: 40px; border: 1px solid #e8d5b7; border-radius: 8px;">
                    <h2 style="color: #1E3A5F; text-align: center;">FROM</h2>
                    <p style="color: #333; font-size: 16px;">%s 인증번호를 안내해 드립니다.</p>
                    <div style="background: #f8f7f4; padding: 20px; text-align: center; border-radius: 4px; margin: 20px 0;">
                        <span style="font-size: 32px; font-weight: bold; color: #1E3A5F; letter-spacing: 8px;">%s</span>
                    </div>
                    <p style="color: #888; font-size: 13px;">인증번호는 5분간 유효합니다.</p>
                    <p style="color: #888; font-size: 13px;">본인이 요청하지 않았다면 이 메일을 무시하세요.</p>
                </div>
                """.formatted(title, code);
    }
}