package com.from.service;

import com.from.domain.ImageResult;
import com.from.dto.ImageDto;

import java.util.List;

public interface IImageService {

    // 이미지 생성 후 DB 저장
    ImageDto.GenerateResponse generateAndSave(byte[] photoBytes, String photoType,
                                              ImageDto.GenerateRequest request, String userId);

    // 유저 이미지 히스토리 조회
    List<ImageResult> getUserImages(String userId);

    // 스타일 옵션 목록 반환
    List<ImageDto.StyleOption> getStyleOptions();
}