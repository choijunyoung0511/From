package com.from.config;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;


//암호화 유틸리티 클래스
// 비번 재사용 = sha-256(단방향), 이메일 저장 = aes-128(양방향)
public class EncryptUtil {

    //비밀번호 해싱할떄 뒤에 붙이는 문자열, 비밀번호 + 비밀 문자열 합침
    private static final String ADD_MESSAGE = "FROM_SALT_2025";

    //벡터 초기화
    private static final String IV = "1234567890123456";

    // 비밀키 고정값
    private static final String KEY = "FromProjectKey16";



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


    //이메일처럼 나중에 복호화가 필요한 값에 사용한다
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


    //암호문 복호화한다 AES128 방식으로 암호문을 복호화
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