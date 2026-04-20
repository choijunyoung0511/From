package com.from.service;

public interface INanobanaService {

    // Gemini API 호출하여 이미지 생성, base64 Data URL 반환
    String generateImage(byte[] photoBytes, String mimeType,
                         String scene, String style, String bookTitle);
}