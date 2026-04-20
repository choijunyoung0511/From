package com.from.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.from.domain.BookReviewDocument;
import com.from.repository.BookReviewMongoRepository;
import com.from.repository.entity.BookEntity;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService implements IReviewService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final IBookService bookService;
    private final IAladinService aladinService;
    private final BookReviewMongoRepository bookReviewMongoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ReviewResult generateAndSave(String userId, Long bookId, String emphasis, String tone,
                                         LocalDate deliveryDate, LocalTime deliveryTime, int paperId) {
        log.info("{}.generateAndSave Start! - userId:{}, bookId:{}", this.getClass().getName(), userId, bookId);

        BookEntity book = bookService.findByUserId(userId).stream()
                .filter(b -> b.getBookId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("책 정보를 찾을 수 없습니다."));

        String toneKr = switch (tone) {
            case "FORMAL"     -> "격식체";
            case "CASUAL"     -> "친근하게";
            case "EMOTIONAL"  -> "감성적으로";
            case "ANALYTICAL" -> "분석적으로";
            default           -> "자연스럽게";
        };

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
                """.formatted(book.getTitle(), book.getAuthor(), emphasis, toneKr);

        String content = callGpt(prompt, 1500);

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
        doc.setIsSent(0);
        doc.setBookTitle(book.getTitle());
        doc.setBookAuthor(book.getAuthor());
        bookReviewMongoRepository.save(doc);

        log.info("{}.generateAndSave End!", this.getClass().getName());
        return new ReviewResult(content, book.getTitle(), book.getAuthor());
    }

    @Override
    public List<Map<String, String>> getAiRecommendations(String userId) {
        log.info("{}.getAiRecommendations Start! - userId:{}", this.getClass().getName(), userId);

        List<BookEntity> userBooks = bookService.findByUserId(userId);
        if (userBooks == null || userBooks.isEmpty()) return List.of();

        String bookList = userBooks.stream()
                .map(b -> b.getTitle() + " - " + b.getAuthor())
                .collect(Collectors.joining("\n"));

        String prompt = """
                사용자가 읽은 책 목록:
                %s

                위 책들을 바탕으로 이 사용자에게 어울리는 책 5권을 추천해줘.
                반드시 아래 JSON 배열 형식으로만 반환하고 다른 말은 절대 하지 마.
                [{"title": "책제목", "author": "저자", "reason": "추천이유 2~3문장"}]
                """.formatted(bookList);

        try {
            String raw = callGpt(prompt, 1000);
            raw = raw.replaceAll("```json", "").replaceAll("```", "").trim();

            List<Map<String, String>> result = objectMapper.readValue(
                    raw,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

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

    private String callGpt(String prompt, int maxTokens) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
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
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("GPT API 호출 실패", e);
            throw new RuntimeException("GPT API 호출에 실패했습니다.", e);
        }
    }
}