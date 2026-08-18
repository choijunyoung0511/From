package com.from.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude API를 호출하여 이미지 생성용 영어 프롬프트를 생성하는 클라이언트.
 * 책 제목, 사용자 장면 설명, 아트 스타일을 받아서
 * NanoBanana 이미지 생성에 최적화된 영어 프롬프트 문자열을 반환한다.
 */
//Service에 외부 API 호출 코드가 섞이면 역할이 모호해지기 때문에,
// 비즈니스 로직과 API 통신 책임을 분리하기 위해 client 폴더로 나눴습니다. 유지보수와 API 교체 대응에도 유리합니다.
@Slf4j
@Component
public class ClaudePromptClient {

    /** application.properties: anthropic.api-key */
    @Value("${anthropic.api-key}")
    private String apiKey;

    /** application.properties: anthropic.model (예: claude-3-5-sonnet-20241022) */
    @Value("${anthropic.model}")
    private String model;

    /** application.properties: anthropic.url (https://api.anthropic.com/v1/messages) */
    @Value("${anthropic.url}")
    private String apiUrl;

    /** Anthropic API 버전 헤더 값 */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** * Claude에게 전달할 시스템 프롬프트
     *  * * 목적: * - 이미지 생성 전문가 역할 부여 *
     *  - 영어 프롬프트만 반환하도록 강제 * - 설명 문장 없이 prompt text만 생성하게 제한 *
     *  * 이유: * NanoBanana(Gemini 이미지 생성)에 * 최적화된 프롬프트를 만들기 위함 */


    private static final String SYSTEM_PROMPT =
            "You are an expert AI image generation prompt engineer. " +
            "Your task is to create detailed, vivid English prompts for AI image generation models. " +
            "The generated image will transform a real person's photo into a book character scene. " +
            "Focus on: art style details, scene composition, lighting, color palette, mood, and character appearance. " +
            "IMPORTANT: Return ONLY the prompt text. No explanations, no extra text, no quotation marks. " +
            "Keep the prompt under 200 words.";

    /**
     * 책 제목, 장면 설명, 아트 스타일을 기반으로 이미지 생성용 영어 프롬프트를 생성한다.
     *
     * @param bookTitle        책 제목 (예: "해리포터와 마법사의 돌")
     * @param sceneDescription 사용자가 입력한 한국어 장면 설명
     * @param style            선택한 아트 스타일 코드 (watercolor, cartoon 등)
     * @return Claude가 생성한 영어 이미지 프롬프트 문자열
     * @throws RuntimeException Claude API 호출 또는 응답 파싱 실패 시
     */
    public String generatePrompt(String bookTitle, String sceneDescription, String style) {
        log.info("{}.generatePrompt Start! - book:{}, style:{}", this.getClass().getName(), bookTitle, style);

        try {
            // 아트 스타일 코드를 Claude가 이해할 수 있는 영어 설명으로 변환
            String styleDescription = translateStyle(style);

            // Claude에게 전달할 사용자 메시지 구성
            String userMessage = String.format(
                    "Create an image generation prompt for the following scene:\n\n" +
                    "Book: \"%s\"\n" +
                    "Scene (Korean): %s\n" +
                    "Art Style: %s\n\n" +
                    "The prompt will be used to transform a real person's photo into this book character scene. " +
                    "Preserve the person's facial features while applying the specified art style. " +
                    "Generate a detailed English image prompt.",
                    bookTitle, sceneDescription, styleDescription
            );

            // Claude API 요청 바디 구성 (Messages API 형식)
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 512,
                    "system", SYSTEM_PROMPT,
                    "messages", List.of(
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            // WebClient로 Claude API 호출
            //“Claude API 응답은 JSON 구조이며 내부 필드 타입이 동적으로 변할 수 있기 때문에,
            // 응답 객체를 범용적으로 처리하기 위해 Map<?, ?> 와일드카드를 사용했습니다
            Map<?, ?> response = WebClient.builder()
                    .build()
                    .post()
                    .uri(apiUrl)
                    .headers(headers -> {
                        headers.set("x-api-key", apiKey);                    // Claude API 인증 헤더
                        headers.set("anthropic-version", ANTHROPIC_VERSION); // API 버전 헤더
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            // 에러 발생 시 응답 body를 로그에 출력하여 정확한 원인 파악
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Claude API Error [{}]: {}", clientResponse.statusCode(), errorBody);
                                        return Mono.error(new RuntimeException("Claude API 오류: " + errorBody));
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Claude API 응답이 비어있습니다.");
            }

            // 응답 JSON에서 생성된 텍스트 추출
            String generatedPrompt = extractText(response);
            log.info("{}.generatePrompt End! - promptLength:{}", this.getClass().getName(), generatedPrompt.length());
            return generatedPrompt;

        } catch (Exception e) {
            log.error("Claude API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("프롬프트 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Claude API 응답 JSON의 content 배열에서 첫 번째 텍스트 블록을 추출한다.
     * 응답 형식: {"content": [{"type": "text", "text": "..."}], ...}
     */
    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.get("content");
        if (contentList == null || contentList.isEmpty()) {
            log.error("Claude 응답 전체: {}", response);
            throw new RuntimeException("Claude 응답에 content가 없습니다.");
        }

        String text = (String) contentList.get(0).get("text");
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Claude 응답 텍스트가 비어있습니다.");
        }
        return text.trim();
    }

    /**
     * 아트 스타일 코드를 Claude가 이해하기 쉬운 영어 설명으로 변환한다.
     * ImageService.getStyleOptions()의 스타일 코드와 동일한 값을 사용한다.
     */
    private String translateStyle(String style) {
        return switch (style) {
            case "watercolor"   -> "soft watercolor painting with transparent washes and gentle color blending";
            case "cartoon"      -> "vibrant cartoon / anime illustration with clean outlines and bold colors";
            case "realistic"    -> "photorealistic style with detailed textures and natural lighting";
            case "oil_painting" -> "classical oil painting style with rich brushstrokes and deep color saturation";
            case "sketch"       -> "detailed pencil sketch with cross-hatching and graphite shading";
            case "fantasy"      -> "fantasy illustration with magical atmosphere, ethereal glow, and dreamlike colors";
            default             -> style;
        };
    }
}