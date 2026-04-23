package com.from.config;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 암호화 유틸리티 클래스.
 *
 * SHA-256 (단방향): 비밀번호 저장에 사용. 솔트(FROM_SALT_2025)를 붙여 레인보우 테이블 공격을 방어한다.
 * AES-256-CBC (양방향): 이메일 저장에 사용. DB에는 암호화된 값을 저장하고, 화면 표시 시 복호화한다.
 */
public class EncryptUtil {

    /** SHA-256 해시 시 비밀번호 뒤에 붙이는 솔트값 */
    private static final String ADD_MESSAGE = "FROM_SALT_2025";

    /** AES-CBC 초기화 벡터 (16바이트 고정) */
    private static final String IV = "1234567890123456";

    /** AES-256 비밀키 (16바이트 = 128bit, 프로젝트 고정값) */
    private static final String KEY = "FromProjectKey16";

    /**
     * SHA-256으로 비밀번호를 단방향 암호화한다.
     * 원래 문자열에 솔트를 붙인 뒤 해시하여 16진수 문자열로 반환한다.
     * 단방향이므로 복호화가 불가능하며, 로그인 시 동일 방식으로 암호화하여 비교한다.
     *
     * @param str 암호화할 평문 비밀번호
     * @return 64자리 16진수 해시 문자열
     */
    public static String encryptSHA256(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((str + ADD_MESSAGE).getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();

            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 암호화 실패", e);
        }
    }

    /**
     * AES-256-CBC 방식으로 문자열을 암호화한다.
     * 이메일처럼 나중에 복호화가 필요한 값에 사용한다.
     *
     * @param str 암호화할 평문 (예: "user@email.com")
     * @return Base64 인코딩된 암호문
     */
    public static String encryptAES(String str) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 암호화 실패", e);
        }
    }

    /**
     * AES-256-CBC 방식으로 암호문을 복호화한다.
     * DB에서 꺼낸 암호화된 이메일을 화면 표시용 평문으로 되돌릴 때 사용한다.
     *
     * @param str Base64 인코딩된 암호문
     * @return 복호화된 평문 (예: "user@email.com")
     */
    public static String decryptAES(String str) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            // Base64 디코딩 후 AES 복호화
            byte[] decoded = Base64.getDecoder().decode(str);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 복호화 실패");
        }
    }
}