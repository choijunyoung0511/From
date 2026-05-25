package com.from.service;

import com.from.dto.ImageRequestDto;
import com.from.dto.ImageResponseDto;
import com.from.dto.ImageResultDto;
import com.from.dto.ImageStyleOptionDto;

import java.util.List;
import java.util.Map;

/**
 * 이미지 생성 서비스 인터페이스.
 * ImageController가 이 인터페이스를 통해 서비스를 호출한다.
 * 구현체는 ImageService(service/impl/)에 있다.
 */
public interface IImageService {


    //claude프롬프트 생성 -> 구글 재미나이로 이미지 생성
    ImageResultDto generateAndSave(byte[] photoBytes, String photoType,
                                   ImageRequestDto request, String userId);


    List<ImageResponseDto> getUserImages(String userId);


    Map<String, Object> getImageDetail(Long imageId);


    // 스타일 목록
    List<ImageStyleOptionDto> getStyleOptions();
}