package com.from.service.impl;

import com.from.service.INanobanaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini API 를 호출하여 이미지를 생성하는 서비스.
 * 사용자 사진(byte[])과 책 정보를 받아 Gemini 에 전송하고,
 * 생성된 이미지를 base64 Data URL 로 반환한다.
 */
@Slf4j
@Service
public class NanobanaService implements INanobanaService {

    @Value("${nanobana.api.key}")
    private String apiKey;

    /** Gemini 이미지 생성 API 엔드포인트 (이미지 출력 지원 모델) */
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";

    /**
     * Gemini API 를 호출하여 사용자 사진을 책 속 장면으로 변환한다.
     *
     * @param photoBytes 사용자 사진 byte[]
     * @param mimeType   사진 MIME 타입 (image/jpeg 등)
     * @param scene      책 속 장면 설명
     * @param style      아트 스타일 (watercolor, cartoon 등)
     * @param bookTitle  책 제목
     * @return base64 Data URL ("data:image/png;base64,...")
     * @throws RuntimeException API 호출 또는 파싱 실패 시
     */
    @Override
    public String generateImage(byte[] photoBytes, String mimeType,
                                String scene, String style, String bookTitle) {
        log.info("{}.generateImage Start!", this.getClass().getName());
        try {
            // part 1: 텍스트 프롬프트
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", buildPrompt(scene, style, bookTitle));

            // part 2: 사용자 사진 (base64 인라인)
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", mimeType != null ? mimeType : "image/jpeg");
            inlineData.put("data", Base64.getEncoder().encodeToString(photoBytes));

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("inline_data", inlineData);

            Map<String, Object> content = new HashMap<>();
            content.put("role", "user");
            content.put("parts", List.of(textPart, imagePart));

            Map<String, Object> genConfig = new HashMap<>();
            genConfig.put("responseModalities", List.of("Text", "Image"));

            Map<String, Object> body = new HashMap<>();
            body.put("contents", List.of(content));
            body.put("generationConfig", genConfig);

            // Gemini 이미지 응답은 base64 인코딩으로 수십 MB가 될 수 있어 버퍼를 20MB로 확장
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                    .build();

            Map<?, ?> response = WebClient.builder()
                    .exchangeStrategies(strategies)
                    .build()
                    .post()
                    .uri(GEMINI_URL + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) throw new RuntimeException("Gemini API 응답이 비어있습니다.");

            log.info("{}.generateImage End!", this.getClass().getName());
            return extractImageUrl(response);

        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("이미지 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Gemini API 응답 본문에서 base64 이미지 Data URL 을 추출한다.
     */
    @SuppressWarnings("unchecked")
    private String extractImageUrl(Map<?, ?> responseBody) {
        log.info("Gemini 응답 전체: {}", responseBody);

        try {
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) responseBody.get("candidates");

            if (candidates == null || candidates.isEmpty()) {
                log.error("candidates 없음. 전체 응답: {}", responseBody);
                throw new RuntimeException("candidates 가 없습니다.");
            }

            Map<String, Object> content =
                    (Map<String, Object>) candidates.get(0).get("content");

            List<Map<String, Object>> parts =
                    (List<Map<String, Object>>) content.get("parts");

            log.info("parts 개수: {}, parts 내용: {}", parts.size(), parts);

            for (Map<String, Object> part : parts) {
                log.info("part 키 목록: {}", part.keySet());
                if (part.containsKey("inlineData")) {
                    Map<String, Object> inline = (Map<String, Object>) part.get("inlineData");
                    String mime   = (String) inline.get("mimeType");
                    String base64 = (String) inline.get("data");
                    return "data:" + mime + ";base64," + base64;
                }
            }
            throw new RuntimeException("응답에서 이미지 데이터를 찾을 수 없습니다.");

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Gemini 에 전달할 이미지 변환 프롬프트를 생성한다.
     */
    private String buildPrompt(String scene, String style, String bookTitle) {
        return String.format(
                "이 사진 속 인물을 책 '%s'의 주인공으로 변환해주세요. " +
                        "장면: %s. 아트 스타일: %s. " +
                        "인물의 얼굴과 특징을 최대한 유지하면서 해당 스타일로 표현해주세요. " +
                        "고품질, 세밀한 표현으로 생성해주세요.",
                bookTitle, scene, style
        );
    }
}