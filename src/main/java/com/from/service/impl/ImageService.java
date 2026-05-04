package com.from.service.impl;

import com.from.domain.ImageResult;
import com.from.dto.ImageRequestDto;
import com.from.dto.ImageResponseDto;
import com.from.dto.ImageStyleOptionDto;
import com.from.repository.ImageResultRepository;
import com.from.service.IBookService;
import com.from.service.IImageService;
import com.from.service.INanobanaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이미지 생성 비즈니스 로직 서비스.
 * NanobanaService(Gemini API) 를 호출하여 이미지를 생성하고
 * 결과를 imge_results 테이블에 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService implements IImageService {

    private final INanobanaService nanobanaService;
    private final ImageResultRepository imageResultRepository;
    private final IBookService bookService;

    /**
     * 사용자 사진을 Gemini API 로 변환하여 DB에 저장한 뒤 결과를 반환한다.
     */
    @Override
    @Transactional
    public ImageResponseDto generateAndSave(byte[] photoBytes,
                                            String photoType,
                                            ImageRequestDto request,
                                            String userId) {
        log.info("{}.generateAndSave Start! - userId:{}, bookId:{}, style:{}",
                this.getClass().getName(), userId, request.bookId(), request.style());
        try {
            String imageUrl = nanobanaService.generateImage(
                    photoBytes,
                    photoType,
                    request.scene(),
                    request.style(),
                    request.bookTitle()
            );

            ImageResult saved = imageResultRepository.save(ImageResult.builder()
                    .userId(userId)
                    .bookId(request.bookId())
                    .imageUrl(imageUrl)
                    .style(request.style())
                    .build());

            log.info("{}.generateAndSave End! - imageId:{}", this.getClass().getName(), saved.getImageId());

            return ImageResponseDto.builder()
                    .imageId(saved.getImageId())
                    .imageUrl(imageUrl)
                    .style(request.style())
                    .bookTitle(request.bookTitle())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("이미지 생성 실패: {}", e.getMessage());
            return ImageResponseDto.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 특정 유저의 이미지 생성 히스토리를 조회한다.
     * Entity → DTO 변환하여 반환한다.
     */
    @Override
    public List<ImageResponseDto> getUserImages(String userId) {
        log.info("{}.getUserImages Start! - userId:{}", this.getClass().getName(), userId);
        List<ImageResponseDto> result = imageResultRepository
                .findByUserIdOrderByCreateAtDesc(userId)
                .stream()
                .map(img -> ImageResponseDto.builder()
                        .imageId(img.getImageId())
                        .imageUrl(img.getImageUrl())
                        .style(img.getStyle())
                        .success(true)
                        .build())
                .toList();
        log.info("{}.getUserImages End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    /**
     * 이미지 상세 정보를 조회한다.
     * Controller의 Repository 직접 접근을 서비스로 이동.
     */
    @Override
    public Map<String, Object> getImageDetail(Long imageId) {
        log.info("{}.getImageDetail Start! - imageId:{}", this.getClass().getName(), imageId);

        return imageResultRepository.findById(imageId)
                .map(img -> {
                    String bookTitle = bookService.findById(img.getBookId())
                            .map(b -> b.title()).orElse("-");
                    Map<String, Object> body = new HashMap<>();
                    body.put("imageUrl",  img.getImageUrl());
                    body.put("style",     img.getStyle());
                    body.put("bookId",    img.getBookId());
                    body.put("bookTitle", bookTitle);
                    log.info("{}.getImageDetail End!", this.getClass().getName());
                    return body;
                })
                .orElse(null);
    }

    /**
     * 스타일 선택 화면에 표시할 옵션 목록을 반환한다.
     */
    @Override
    public List<ImageStyleOptionDto> getStyleOptions() {
        return Arrays.asList(
                new ImageStyleOptionDto("watercolor",   "수채화",         "부드럽고 몽환적인 수채화 스타일"),
                new ImageStyleOptionDto("cartoon",      "만화/애니메이션", "생동감 있는 애니메이션 스타일"),
                new ImageStyleOptionDto("realistic",    "사실적",         "실제 사진처럼 정교한 스타일"),
                new ImageStyleOptionDto("oil_painting", "유화",           "고전 명화 같은 유화 스타일"),
                new ImageStyleOptionDto("sketch",       "스케치",         "연필 드로잉 스타일"),
                new ImageStyleOptionDto("fantasy",      "판타지",         "동화 속 환상적인 스타일")
        );
    }
}