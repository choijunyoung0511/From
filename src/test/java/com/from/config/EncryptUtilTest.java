package com.from.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptUtilTest {

    @Test
    void sha256_동일한_입력은_항상_동일한_해시를_생성한다() {
        String hash1 = EncryptUtil.encryptSHA256("password123");
        String hash2 = EncryptUtil.encryptSHA256("password123");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void sha256_다른_입력은_다른_해시를_생성한다() {
        String hash1 = EncryptUtil.encryptSHA256("password123");
        String hash2 = EncryptUtil.encryptSHA256("password124");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void sha256_원문을_그대로_노출하지_않는다() {
        String raw = "myPlainPassword";
        String hash = EncryptUtil.encryptSHA256(raw);

        assertThat(hash).doesNotContain(raw);
        assertThat(hash).hasSize(64); // SHA-256 hex 문자열 길이
    }

    @Test
    void aes_암호화한_값을_복호화하면_원문과_같다() {
        String original = "test@example.com";

        String encrypted = EncryptUtil.encryptAES(original);
        String decrypted = EncryptUtil.decryptAES(encrypted);

        assertThat(decrypted).isEqualTo(original);
        assertThat(encrypted).isNotEqualTo(original); // 암호문은 평문과 달라야 함
    }

    @Test
    void aes_잘못된_암호문_복호화는_예외를_던진다() {
        assertThatThrownBy(() -> EncryptUtil.decryptAES("not-a-valid-base64-cipher"))
                .isInstanceOf(RuntimeException.class);
    }
}
