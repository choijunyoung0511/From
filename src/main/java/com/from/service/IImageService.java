package com.from.service;

import com.from.domain.ImageResult;
import com.from.dto.ImageRequestDto;
import com.from.dto.ImageResponseDto;
import com.from.dto.ImageStyleOptionDto;

import java.util.List;

/**
 * 이미지 생성 서비스 인터페이스.
 * ImageController가 이 인터페이스를 통해 서비스를 호출한다.
 * 구현체는 ImageService(service/impl/)에 있다.
 */
public interface IImageService {

    /**
     * 사용자 사진을 Gemini API로 변환하여 이미지를 생성하고 DB에 저장한다.
     *
     * @param photoBytes 세션에 저장된 사용자 사진 byte[]
     * @param photoType  사진 MIME 타입 (예: image/jpeg)
     * @param request    생성 요청 파라미터 (bookId, bookTitle, scene, style)
     * @param userId     요청 사용자의 로그인 ID
     * @return 생성 결과 DTO (성공: imageId·imageUrl, 실패: errorMessage)
     */
    ImageResponseDto generateAndSave(byte[] photoBytes, String photoType,
                                     ImageRequestDto request, String userId);

    /**
     * 특정 유저의 이미지 생성 히스토리를 최신순으로 조회한다.
     *
     * @param userId 요청 사용자의 로그인 ID
     * @return 이미지 결과 목록
     */
    List<ImageResult> getUserImages(String userId);

    /**
     * 스타일 선택 화면에 표시할 아트 스타일 옵션 목록을 반환한다.
     *
     * @return 수채화·만화·사실적 등 6종의 스타일 옵션 목록
     */
    List<ImageStyleOptionDto> getStyleOptions();
}