package com.from.service;

public interface IEmailService {

    // 인증번호 이메일 발송 (SIGNUP / FIND_ID / FIND_PASSWORD)
    void sendVerificationCode(String toEmail, String code, String purpose);
}