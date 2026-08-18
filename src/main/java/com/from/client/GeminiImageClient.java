package com.from.client;

import com.from.dto.GeminiImageRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini API를 호출하여 이미지를 생성하는 클라이언트.
 * 사용자 사진 + Claude가 생성한 영어 프롬프트 → Gemini → base64 이미지 반환
 */
@Slf4j
@Component
public class GeminiImageClient {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.url}")
    private String apiUrl;

    private static final int MAX_BUFFER_SIZE = 20 * 1024 * 1024; // 20MB

    public String generateImage(GeminiImageRequestDto request) {
        log.info("{}.generateImage Start! - promptLength:{}", this.getClass().getName(), request.prompt().length());

        try {
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", request.prompt());

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", request.imageMimeType());
            inlineData.put("data", request.imageBase64());

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

            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE))
                    .build();

            Map<?, ?> response = WebClient.builder()//모든 타입 가능
                    .exchangeStrategies(strategies)
                    .build()
                    .post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) throw new RuntimeException("Gemini API 응답이 비어있습니다.");

            log.info("{}.generateImage End!", this.getClass().getName());
            return extractImageDataUrl(response);

        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("이미지 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractImageDataUrl(Map<?, ?> responseBody) {
        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) responseBody.get("candidates");

        if (candidates == null || candidates.isEmpty()) {
            log.error("Gemini 응답 전체: {}", responseBody);
            throw new RuntimeException("이미지 생성 응답에 candidates가 없습니다.");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

        for (Map<String, Object> part : parts) {
            if (part.containsKey("inlineData")) {
                Map<String, Object> inline = (Map<String, Object>) part.get("inlineData");
                String mime   = (String) inline.get("mimeType");
                String base64 = (String) inline.get("data");
                return "data:" + mime + ";base64," + base64;
            }
        }
        throw new RuntimeException("Gemini 응답에서 이미지 데이터를 찾을 수 없습니다.");
    }
}