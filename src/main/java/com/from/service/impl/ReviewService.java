package com.from.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.from.domain.BookReviewDocument;
import com.from.dto.BookSearchDTO;
import com.from.repository.BookReviewMongoRepository;
import com.from.service.IAladinService;
import com.from.service.IBookService;
import com.from.service.IReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 독후감 생성 서비스 구현체.
 * OpenAI GPT API를 호출하여 독후감을 생성하고 MongoDB에 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService implements IReviewService {

    /** application.properties의 openai.api.key 값 주입 */
    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final IBookService bookService;
    private final IAladinService aladinService;
    private final BookReviewMongoRepository bookReviewMongoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GPT에게 독후감 작성을 요청하고 결과를 MongoDB(aireviews 컬렉션)에 저장한다.
     * 저장된 독후감은 ReviewScheduler가 지정 날짜·시각에 이메일로 발송한다.
     */
    @Override
    public ReviewResult generateAndSave(String userId, Long bookId, String emphasis, String tone,
                                         LocalDate deliveryDate, LocalTime deliveryTime, int paperId) {
        log.info("{}.generateAndSave Start! - userId:{}, bookId:{}", this.getClass().getName(), userId, bookId);

        // 유저가 등록한 책 목록에서 해당 bookId의 책 정보를 찾는다
        BookSearchDTO book = bookService.findByUserId(userId).stream()
                .filter(b -> b.bookId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("책 정보를 찾을 수 없습니다."));

        // 영문 tone 코드를 GPT 프롬프트에 쓸 한국어로 변환
        String toneKr = switch (tone) {
            case "FORMAL"     -> "격식체";
            case "CASUAL"     -> "친근하게";
            case "EMOTIONAL"  -> "감성적으로";
            case "ANALYTICAL" -> "분석적으로";
            default           -> "자연스럽게";
        };

        // GPT에게 전달할 독후감 작성 프롬프트 구성
        String prompt = """
                다음 책에 대한 독후감을 작성해주세요.

                책 제목: %s
                저자: %s
                강조할 내용: %s
                작성 톤: %s

                조건:
                - 미래의 나에게 보내는 편지 형식으로 작성해주세요.
                - 700~1000자 분량으로 작성해주세요.
                - 책의 핵심 메시지와 강조할 내용을 잘 녹여주세요.
                - 독후감만 반환하고 다른 말은 하지 마세요.
                """.formatted(book.title(), book.author(), emphasis, toneKr);

        // GPT API 호출 (최대 1500 토큰)
        String content = callGpt(prompt, 1500);

        // 생성된 독후감을 MongoDB에 저장 (이메일 예약 발송 대기 상태)
        BookReviewDocument doc = new BookReviewDocument();
        doc.setUserId(userId);
        doc.setBookId(bookId);
        doc.setPaperId(paperId);
        doc.setEmphasisContent(emphasis);
        doc.setTone(tone);
        doc.setDeliveryDate(deliveryDate);
        doc.setDeliveryTime(deliveryTime);
        doc.setAiContent(content);
        doc.setGenerationStatus("COMPLETED");
        doc.setIsSent(0); // 0 = 미발송 (ReviewScheduler가 예약 시각에 1로 변경)
        doc.setBookTitle(book.title());
        doc.setBookAuthor(book.author());
        bookReviewMongoRepository.save(doc);

        log.info("{}.generateAndSave End!", this.getClass().getName());
        return new ReviewResult(content, book.title(), book.author());
    }

    /**
     * 유저의 독서 이력(읽은 책 목록)을 GPT에게 제공하여 책 5권을 추천받는다.
     * GPT 응답을 JSON으로 파싱하고, 각 추천 책의 표지 이미지를 알라딘 API로 추가한다.
     */
    @Override
    public List<Map<String, String>> getAiRecommendations(String userId) {
        log.info("{}.getAiRecommendations Start! - userId:{}", this.getClass().getName(), userId);

        List<BookSearchDTO> userBooks = bookService.findByUserId(userId);
        if (userBooks == null || userBooks.isEmpty()) return List.of();

        // 유저가 읽은 책 목록을 "제목 - 저자" 형태로 변환
        String bookList = userBooks.stream()
                .map(b -> b.title() + " - " + b.author())
                .collect(Collectors.joining("\n"));

        // GPT에게 JSON 배열 형식으로만 응답하도록 명시적으로 지시
        String prompt = """
                사용자가 읽은 책 목록:
                %s

                위 책들을 바탕으로 이 사용자에게 어울리는 책 5권을 추천해줘.
                반드시 아래 JSON 배열 형식으로만 반환하고 다른 말은 절대 하지 마.
                [{"title": "책제목", "author": "저자", "reason": "추천이유 2~3문장"}]
                """.formatted(bookList);

        try {
            String raw = callGpt(prompt, 1000);
            // GPT가 ```json ... ``` 코드 블록으로 감싸서 반환하는 경우 제거
            raw = raw.replaceAll("```json", "").replaceAll("```", "").trim();

            List<Map<String, String>> result = objectMapper.readValue(
                    raw,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            // 각 추천 책의 표지 이미지를 알라딘 API로 조회하여 cover 필드에 추가
            for (Map<String, String> rec : result) {
                rec.put("cover", aladinService.searchCover(rec.get("title")));
            }

            log.info("{}.getAiRecommendations End! - {}권", this.getClass().getName(), result.size());
            return result;

        } catch (Exception e) {
            log.error("AI 추천 파싱 오류", e);
            throw new RuntimeException("추천 생성 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 유저의 독후감 이력을 MongoDB에서 조회한다.
     */
    @Override
    public List<BookReviewDocument> getReviewsByUserId(String userId) {
        log.info("{}.getReviewsByUserId Start! - userId:{}", this.getClass().getName(), userId);
        List<BookReviewDocument> result = bookReviewMongoRepository.findByUserId(userId);
        log.info("{}.getReviewsByUserId End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    /**
     * OpenAI GPT API를 호출하여 텍스트 응답을 받는다.
     * WebClient를 사용하여 비동기 HTTP 요청을 동기적으로 처리한다(block()).
     *
     * @param prompt    GPT에게 전달할 프롬프트
     * @param maxTokens 최대 응답 토큰 수 (1토큰 ≈ 한글 0.5자)
     * @return GPT의 텍스트 응답
     */
    private String callGpt(String prompt, int maxTokens) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini"); // 비용 효율적인 GPT 모델
            body.put("max_tokens", maxTokens);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            String response = WebClient.create("https://api.openai.com")
                    .post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 비동기 결과를 동기적으로 기다림

            // JSON 응답에서 choices[0].message.content 값을 추출
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("GPT API 호출 실패", e);
            throw new RuntimeException("GPT API 호출에 실패했습니다.", e);
        }
    }
}