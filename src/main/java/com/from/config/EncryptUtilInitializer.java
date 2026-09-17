package com.from.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// EncryptUtil은 정적 유틸이라 스프링 빈으로 주입받을 수 없어서,
// 부팅 시 encrypt.aes.key/encrypt.aes.iv(${ENCRYPT_AES_KEY}/${ENCRYPT_AES_IV}) 값을 읽어 EncryptUtil에 채워준다.
@Component
public class EncryptUtilInitializer {

    @Value("${encrypt.aes.key}")
    private String aesKey;

    @Value("${encrypt.aes.iv}")
    private String aesIv;

    @PostConstruct
    public void init() {
        EncryptUtil.init(aesKey, aesIv);
    }
}
