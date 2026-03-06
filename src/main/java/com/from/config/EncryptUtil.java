package com.from.config;

import org.apache.logging.log4j.message.Message;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class EncryptUtil {
    private static final String ADD_MESSAGE = "FROM_SALT_2025";

    private static final String IV = "1234567890123456";

    private static final String KEY = "FromProjectKey16";

    public static String encryptSHA256(String str){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((str + ADD_MESSAGE).getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();

            StringBuilder sb =new StringBuilder();
            for (byte b : bytes){
                sb.append(String.format("%02x",b));
            }
            return sb.toString();
        } catch (Exception e){
            throw new RuntimeException("SHA-256 암호화 실패",e);

        }
    }

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
    public static String decryptAES(String str){
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8),"AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decoded = Base64.getDecoder().decode(str);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e){
            throw new RuntimeException("AES복호화 실패");
        }



    }
}
