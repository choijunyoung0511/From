package com.from.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 이메일 발송 서비스
 * - 회원가입 인증번호 발송
 * - 아이디 찾기 인증번호 발송
 * - 비밀번호 찾기 인증번호 발송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 인증번호 이메일 발송
     * @param toEmail  수신 이메일
     * @param code     6자리 인증번호
     * @param purpose  목적 (SIGNUP / FIND_ID / FIND_PASSWORD)
     */
    public void sendVerificationCode(String toEmail, String code, String purpose) {
        try {
            String subject = getSubject(purpose);
            String content = getContent(code, purpose);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);   // 발신자 주소 = 로그인한 네이버 계정
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true); // true = HTML 형식

            mailSender.send(message);
            log.info("이메일 발송 성공 - from: {}, to: {}, purpose: {}", fromEmail, toEmail, purpose);

        } catch (Exception e) {
            log.error("이메일 발송 실패 - from: {}, to: {}, error: {}", fromEmail, toEmail, e.getMessage());
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }

    // 목적별 이메일 제목
    private String getSubject(String purpose) {
        return switch (purpose) {
            case "SIGNUP" -> "[FROM] 회원가입 이메일 인증번호";
            case "FIND_ID" -> "[FROM] 아이디 찾기 인증번호";
            case "FIND_PASSWORD" -> "[FROM] 비밀번호 찾기 인증번호";
            default -> "[FROM] 인증번호";
        };
    }

    // 목적별 이메일 본문 (HTML)
    private String getContent(String code, String purpose) {
        String title = switch (purpose) {
            case "SIGNUP" -> "회원가입 이메일 인증";
            case "FIND_ID" -> "아이디 찾기 인증";
            case "FIND_PASSWORD" -> "비밀번호 찾기 인증";
            default -> "이메일 인증";
        };

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