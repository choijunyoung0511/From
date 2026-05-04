package com.from.service;

import com.from.dto.ImageRequestDto;
import com.from.dto.ImageResponseDto;
import com.from.dto.ImageStyleOptionDto;

import java.util.List;
import java.util.Map;

/**
 * 이미지 생성 서비스 인터페이스.
 * ImageController가 이 인터페이스를 통해 서비스를 호출한다.
 * 구현체는 ImageService(service/impl/)에 있다.
 */
public interface IImageService {

    /**
     * 사용자 사진을 Gemini API로 변환하여 이미지를 생성하고 DB에 저장한다.
     */
    ImageResponseDto generateAndSave(byte[] photoBytes, String photoType,
                                     ImageRequestDto request, String userId);

    /**
     * 특정 유저의 이미지 생성 히스토리를 최신순으로 조회한다.
     * Entity 대신 DTO로 반환한다.
     */
    List<ImageResponseDto> getUserImages(String userId);

    /**
     * 이미지 상세 정보를 조회한다.
     *
     * @param imageId 조회할 이미지 ID
     * @return {imageUrl, style, bookId, bookTitle} 또는 null
     */
    Map<String, Object> getImageDetail(Long imageId);

    /**
     * 스타일 선택 화면에 표시할 아트 스타일 옵션 목록을 반환한다.
     */
    List<ImageStyleOptionDto> getStyleOptions();
}