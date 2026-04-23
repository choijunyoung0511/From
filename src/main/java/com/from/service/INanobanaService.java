package com.from.service;

/**
 * Google Gemini API 호출 서비스 인터페이스.
 * 이미지 생성(ImageService)이 이 인터페이스를 통해 Gemini API를 호출한다.
 * 구현체는 NanobanaService(service/impl/)에 있다.
 */
public interface INanobanaService {

    /**
     * Gemini API를 호출하여 사용자 사진을 책 속 장면 이미지로 변환한다.
     *
     * @param photoBytes 사용자 사진 byte[] (세션에서 꺼낸 값)
     * @param mimeType   사진 MIME 타입 (예: image/jpeg)
     * @param scene      책 속 장면 설명 (사용자 입력)
     * @param style      아트 스타일 코드 (watercolor, cartoon 등)
     * @param bookTitle  책 제목 (Gemini 프롬프트에 포함)
     * @return base64 Data URL 형태의 생성된 이미지 ("data:image/png;base64,...")
     * @throws RuntimeException API 호출 실패 또는 응답 파싱 실패 시
     */
    String generateImage(byte[] photoBytes, String mimeType,
                         String scene, String style, String bookTitle);
}