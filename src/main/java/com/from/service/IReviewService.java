package com.from.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public interface IReviewService {

    record ReviewResult(String content, String bookTitle, String bookAuthor) {}

    // AI 독후감 생성 + MongoDB 저장
    ReviewResult generateAndSave(String userId, Long bookId, String emphasis, String tone,
                                  LocalDate deliveryDate, LocalTime deliveryTime, int paperId);

    // 독서 이력 기반 GPT 책 5권 추천
    List<Map<String, String>> getAiRecommendations(String userId);
}