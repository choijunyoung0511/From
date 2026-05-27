package com.from.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.from.repository.document.BookReviewDocument;
import com.from.dto.BookSearchDto;
import com.from.repository.BookReviewMongoRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
//독후감 생성 서비스
public class ReviewService implements IReviewService {
    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final IBookService bookService;
    private final BookReviewMongoRepository bookReviewMongoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    //프롬프트 작성
    private static final String REVIEW_SYSTEM_PROMPT = """
            당신은 책을 깊이 읽고 삶과 연결 짓는 글쓰기를 잘하는 작가입니다.
            독자의 감정과 경험에 공감하며, 문학적 표현과 진솔한 성찰을 자연스럽게 녹여냅니다.
            미래의 나에게 보내는 편지를 쓸 때, 단순한 줄거리 요약이 아닌 책이 남긴 울림을 전달합니다.
            요청받은 문체 지침과 편지 구조를 반드시 따릅니다.
            """;
    //토큰 절약을 위해서 글자수 제한
    private static final int DESC_MAX_LENGTH = 500;



    // 몽고컬력션에 저장후 저장된 독후감은 Review스케줄러가 지정한 날짜 및 시각에 이메일로 발송함
    @Override
    public ReviewResult generateAndSave(String userId, Long bookId, String emphasis, String tone,
                                        LocalDate deliveryDate, LocalTime deliveryTime, int paperId) {
        log.info("{}.generateAndSave Start! - userId:{}, bookId:{}, tone:{}, deliveryDate:{}",
                this.getClass().getName(), userId, bookId, tone, deliveryDate);

        // #1 유저가 등록한 책 목록에서 해당 bookId의 책 정보를 찾는다
        BookSearchDto book = bookService.findByUserId(userId).stream()
                .filter(b -> b.bookId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("책 정보를 찾을 수 없습니다."));

        // #2 system / user 프롬프트 각각 빌드 후 GPT 호출
        String userPrompt = buildReviewUserPrompt(book, emphasis, tone);
        String content    = callGpt(REVIEW_SYSTEM_PROMPT, userPrompt, 1500);

        // #3 생성된 독후감을 MongoDB에 저장 (이메일 예약 발송 대기 상태)
        BookReviewDocument doc = BookReviewDocument.builder()
                .userId(userId)
                .bookId(bookId)
                .paperId(paperId)
                .emphasisContent(emphasis)
                .tone(tone)
                .deliveryDate(deliveryDate)
                .deliveryTime(deliveryTime)
                .aiContent(content)
                .generationStatus("COMPLETED")
                .isSent(0)
                .bookTitle(book.title())
                .bookAuthor(book.author())
                .build();
        bookReviewMongoRepository.save(doc);

        log.info("{}.generateAndSave End!", this.getClass().getName());
        return new ReviewResult(content, book.title(), book.author());
    }


    //독후감 이력 조회
    @Override
    public List<BookReviewDocument> getReviewsByUserId(String userId) {
        log.info("{}.getReviewsByUserId Start! - userId:{}", this.getClass().getName(), userId);
        List<BookReviewDocument> result = bookReviewMongoRepository.findByUserId(userId);
        log.info("{}.getReviewsByUserId End! - {}건", this.getClass().getName(), result.size());
        return result;
    }


    private String buildReviewUserPrompt(BookSearchDto book, String emphasis, String tone) {
        // 줄거리가 있으면 500자로 truncate(커팅)하여 삽입, 없으면 빈 문자열
        String descSection = "";
        if (book.description() != null && !book.description().isBlank()) {
            descSection = "\n책 소개: " + truncate(book.description(), DESC_MAX_LENGTH);
        }

        // 톤 코드 → 구체적인 문체 지침으로 변환
        String toneGuide = buildToneGuide(tone);

        return """
                다음 책에 대한 독후감을 작성해주세요.

                책 제목: %s
                저자: %s%s
                강조할 내용: %s

                [문체 지침]
                %s

                [편지 구조 — 반드시 순서대로 작성]
                - 도입: 이 책을 읽게 된 순간, 또는 처음 펼쳤을 때의 인상으로 편지를 시작하세요.
                - 본론: 책의 핵심 메시지와 위의 강조할 내용을 자신의 삶과 연결하여 풀어쓰세요.
                        책 소개에 나온 구체적인 장면이나 문장을 최소 하나 이상 자연스럽게 언급하세요.
                - 결론: 미래의 내가 이 편지를 읽을 때 가장 기억했으면 하는 한 문장으로 마무리하세요.

                [출력 조건]
                - 미래의 나에게 보내는 편지 형식으로 작성하세요.
                - 700~1000자 분량으로 작성하세요.
                - 독후감 본문만 반환하고 제목이나 다른 말은 하지 마세요.
                """.formatted(book.title(), book.author(), descSection, emphasis, toneGuide);
    }

    //톤코드
    private String buildToneGuide(String tone) {
        return switch (tone) {
            case "FORMAL" -> """
                    격식 있는 경어체를 사용하세요.
                    논리적이고 단정한 문장 구조로 책의 가치를 정중하게 서술하세요.
                    감정 표현보다 책이 전달하는 메시지의 의미와 가치를 강조하세요.
                    """;
            case "CASUAL" -> """
                    친한 친구에게 편지를 쓰듯 편안하고 솔직하게 쓰세요.
                    구어체와 짧은 문장을 섞어 생동감 있게 표현하세요.
                    어렵거나 딱딱한 표현은 피하고, 자연스러운 일상 언어를 사용하세요.
                    """;
            case "EMOTIONAL" -> """
                    시적이고 감성적인 표현을 사용하세요.
                    책을 읽으며 느낀 감정의 변화를 구체적으로 묘사하세요.
                    은유와 비유를 자연스럽게 활용하고, 독자의 감정을 자극하는 문장을 써주세요.
                    """;
            case "ANALYTICAL" -> """
                    책의 핵심 주제를 논리적으로 분석하고 사회적·역사적 맥락과 연결하세요.
                    인과관계를 명확히 서술하고, 다양한 관점에서 비판적으로 바라보는 시각도 담아주세요.
                    감성 표현보다 근거와 논리를 중심으로 전개하세요.
                    """;
            default -> "자연스럽고 진솔하게 쓰세요.";
        };
    }

    // ── GPT API 호출 ─────────────────────────────────────────────────────────

    //gpt 호출하여 텍스트 응답을 받는다.
    private String callGpt(String systemPrompt, String userPrompt, int maxTokens) {
        try {
            // system 메시지 유무에 따라 messages 배열 구성
            List<Map<String, String>> messages;
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                // 독후감: system(페르소나) + user(요청) 분리
                messages = List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userPrompt)
                );
            } else {
                // 추천: user 단일 메시지
                messages = List.of(
                        Map.of("role", "user", "content", userPrompt)
                );
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model",      "gpt-4o-mini"); // 비용 효율적인 GPT 모델
            body.put("max_tokens", maxTokens);
            body.put("messages",   messages);

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
            log.error("GPT API 호출 실패 - maxTokens:{}, error:{}", maxTokens, e.getMessage(), e);
            throw new RuntimeException("GPT API 호출에 실패했습니다.", e);
        }
    }

    // 문자열 초과시 maxLength 이하로 자른다
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text == null ? "" : text;
        return text.substring(0, maxLength) + "...";
    }
}