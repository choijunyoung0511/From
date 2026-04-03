//package com.from.service;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.*;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class NanobanaService {
//
//    @Value("${nanobana.api.key}")
//    private String apiKey;
//
//    private final RestTemplate restTemplate;
//
//
//    private static final String GEMINI_URL =
//            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";
//
//    /**
//     * Gemini(나노바나나) API 호출 → base64 Data URL 반환
//     *
//     * @param photoBytes 사용자 사진 byte[]
//     * @param mimeType   사진 MIME 타입 (image/jpeg 등)
//     * @param scene      책 장면 설명
//     * @param style      스타일
//     * @param bookTitle  책 제목
//     */
//    public String generateImage(byte[] photoBytes, String mimeType,
//                                String scene, String style, String bookTitle) {
//        try {
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//
//            // part 1: 텍스트 프롬프트
//            Map<String, Object> textPart = new HashMap<>();
//            textPart.put("text", buildPrompt(scene, style, bookTitle));
//
//            // part 2: 사용자 사진 (base64 인라인)
//            Map<String, Object> inlineData = new HashMap<>();
//            inlineData.put("mime_type", mimeType != null ? mimeType : "image/jpeg");
//            inlineData.put("data", Base64.getEncoder().encodeToString(photoBytes));
//
//            Map<String, Object> imagePart = new HashMap<>();
//            imagePart.put("inline_data", inlineData);
//
//            // contents
//            Map<String, Object> content = new HashMap<>();
//            content.put("parts", List.of(textPart, imagePart));
//
//            // generationConfig
//            Map<String, Object> genConfig = new HashMap<>();
//            genConfig.put("response_modalities", List.of("IMAGE", "TEXT"));
//
//            Map<String, Object> body = new HashMap<>();
//            body.put("contents", List.of(content));
//            body.put("generationConfig", genConfig);
//
//            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
//            String url = GEMINI_URL + "?key=" + apiKey;
//
//            ResponseEntity<Map> response = restTemplate.exchange(
//                    url, HttpMethod.POST, request, Map.class);
//
//            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
//                return extractImageUrl(response.getBody());
//            }
//
//            throw new RuntimeException("Gemini API 응답 오류: " + response.getStatusCode());
//
//        } catch (Exception e) {
//            log.error("Gemini API 호출 실패: {}", e.getMessage());
//            throw new RuntimeException("이미지 생성에 실패했습니다: " + e.getMessage(), e);
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    private String extractImageUrl(Map<String, Object> responseBody) {
//        log.info("Gemini 응답 전체: {}", responseBody);
//
//        try {
//            List<Map<String, Object>> candidates =
//                    (List<Map<String, Object>>) responseBody.get("candidates");
//
//            if (candidates == null || candidates.isEmpty()) {
//                log.error("candidates 없음. 전체 응답: {}", responseBody);
//                throw new RuntimeException("candidates가 없습니다.");
//            }
//
//            Map<String, Object> content =
//                    (Map<String, Object>) candidates.get(0).get("content");
//
//            List<Map<String, Object>> parts =
//                    (List<Map<String, Object>>) content.get("parts");
//
//            log.info("parts 개수: {}, parts 내용: {}", parts.size(), parts);
//
//            for (Map<String, Object> part : parts) {
//                log.info("part 키 목록: {}", part.keySet());
//                if (part.containsKey("inlineData")) {
//                    Map<String, Object> inlineData = (Map<String, Object>) part.get("inlineData");
//                    String mime   = (String) inlineData.get("mime_type");
//                    String base64 = (String) inlineData.get("data");
//                    return "data:" + mime + ";base64," + base64;
//                }
//            }
//            throw new RuntimeException("응답에서 이미지 데이터를 찾을 수 없습니다.");
//        } catch (RuntimeException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new RuntimeException("파싱 실패: " + e.getMessage(), e);
//        }
//    }
//
//    private String buildPrompt(String scene, String style, String bookTitle) {
//        return String.format(
//                "이 사진 속 인물을 책 '%s'의 주인공으로 변환해주세요. " +
//                        "장면: %s. 아트 스타일: %s. " +
//                        "인물의 얼굴과 특징을 최대한 유지하면서 해당 스타일로 표현해주세요. " +
//                        "고품질, 세밀한 표현으로 생성해주세요.",
//                bookTitle, scene, style
//        );
//    }
//}