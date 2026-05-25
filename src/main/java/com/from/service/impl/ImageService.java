package com.from.service.impl;

import com.from.client.ClaudePromptClient;
import com.from.client.NanoBananaImageClient;
import com.from.dto.nano.NanoBananaRequest;
import com.from.domain.ImageResult;
import com.from.dto.ImageRequestDto;
import com.from.dto.ImageResponseDto;
import com.from.dto.ImageResultDto;
import com.from.dto.ImageStyleOptionDto;
import com.from.repository.ImageResultRepository;
import com.from.service.IBookService;
import com.from.service.IImageService;
import com.from.service.IS3UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이미지 생성 비즈니스 로직 서비스.
 *
 * 생성 흐름:
 *  1. 사용자 사진 → S3 inputs/ 폴더에 업로드 (원본 보관)
 *  2. ClaudePromptClient → 책/장면/스타일 기반 영어 프롬프트 생성
 *  3. NanoBananaImageClient → 사진 + 프롬프트로 이미지 생성
 *  4. 결과 이미지 → S3 outputs/ 폴더에 업로드 (base64 방식인 경우)
 *  5. imge_results 테이블에 결과 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService implements IImageService {
    //클로드 전용 클라이언트(영문으로 작성 하여 재미나이에 던짐)
    private final ClaudePromptClient claudePromptClient;
    //재미나이 사용 클라이언트
    private final NanoBananaImageClient nanoBananaImageClient;
    //이미지 생성 후 저장소
    private final ImageResultRepository imageResultRepository;
    //책 정보 조회
    private final IBookService bookService;
    //아마존 s3에 이미지 저장
    private final IS3UploadService s3UploadService;

    // 클로드 + 제미나이를 사용하여 사용자 사진을 책 속 주인공 이미지로 변환후 DB에 저장
    @Override
    @Transactional
    public ImageResultDto generateAndSave(byte[] photoBytes,
                                          String photoType,
                                          ImageRequestDto request,
                                          String userId) {
        log.info("{}.generateAndSave Start! - userId:{}, bookId:{}, style:{}",
                this.getClass().getName(), userId, request.bookId(), request.style());

        // Step 1: 사용자 원본 사진을 S3 inputs/ 폴더에 업로드 (변환 전 원본 보관)
        String inputImageUrl = s3UploadService.upload(photoBytes, photoType, "inputs");
        log.info("원본 사진 S3 업로드 완료: {}", inputImageUrl);

        // Step 2: Claude API 호출 → 책/장면/스타일 기반 영어 이미지 프롬프트 생성
        log.info("Claude 프롬프트 생성 중 - book:{}, style:{}", request.bookTitle(), request.style());
        String generatedPrompt = claudePromptClient.generatePrompt(
                request.bookTitle(),
                request.scene(),
                request.style()
        );
        log.info("Claude 프롬프트 생성 완료: {}", generatedPrompt);

        // Step 3: NanoBananaRequest 구성 후 이미지 생성 API 호출
        log.info("NanoBanana 이미지 생성 중...");
        String base64Photo = java.util.Base64.getEncoder().encodeToString(photoBytes);
        String safeType    = (photoType != null) ? photoType : "image/jpeg";

        NanoBananaRequest imageRequest = new NanoBananaRequest(generatedPrompt, base64Photo, safeType);
        String generatedImage = nanoBananaImageClient.generateImage(imageRequest);

        // Step 4: 생성된 이미지 결과 처리 (URL 또는 base64 Data URL)
        String imageUrl;
        if (generatedImage.startsWith("data:")) {
            // base64 Data URL ("data:image/png;base64,...") → 디코딩 후 S3 outputs/ 업로드
            String[] parts = generatedImage.split(",", 2);
            String mime = parts[0].replace("data:", "").replace(";base64", "");
            byte[] imageBytes = Base64.getDecoder().decode(parts[1]);
            imageUrl = s3UploadService.upload(imageBytes, mime, "outputs");
            log.info("생성 이미지 S3 업로드 완료 (base64→S3): {}", imageUrl);
        } else {
            // 이미 URL인 경우 그대로 사용
            imageUrl = generatedImage;
            log.info("생성 이미지 URL 수신: {}", imageUrl);
        }

        // Step 5: 생성 결과를 DB에 저장
        ImageResult saved = imageResultRepository.save(ImageResult.builder()
                .userId(userId)
                .bookId(request.bookId())
                .inputImageUrl(inputImageUrl)
                .imageUrl(imageUrl)
                .style(request.style())
                .build());

        log.info("{}.generateAndSave End! - imageId:{}", this.getClass().getName(), saved.getImageId());

        // ImageResultDto로 변환하여 반환
        return new ImageResultDto(
                saved.getImageId(),
                imageUrl,
                generatedPrompt,
                request.bookTitle(),
                request.style(),
                request.scene(),
                saved.getCreateAt()
        );
    }

    /**
     * 특정 유저의 이미지 생성 히스토리를 최신순으로 조회한다.
     */
    @Override
    public List<ImageResponseDto> getUserImages(String userId) {
        //마이페이지 에서 이미지 생성 한거 조회

        log.info("{}.getUserImages Start! - userId:{}", this.getClass().getName(), userId);
        List<ImageResponseDto> result = imageResultRepository
                .findByUserIdOrderByCreateAtDesc(userId)
                .stream()
                .map(img -> {
                    String bookTitle = bookService.findById(img.getBookId())
                            .map(b -> b.title()).orElse("-");
                    return ImageResponseDto.builder()
                            .imageId(img.getImageId())
                            .imageUrl(img.getImageUrl())
                            .style(img.getStyle())
                            .bookTitle(bookTitle)
                            .success(true)
                            .build();
                })
                .toList();
        log.info("{}.getUserImages End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    // 이미지 상세 정보를 조회한다 (결과화면 상세 API)
    @Override
    public Map<String, Object> getImageDetail(Long imageId) {
        log.info("{}.getImageDetail Start! - imageId:{}", this.getClass().getName(), imageId);

        return imageResultRepository.findById(imageId)
                .map(img -> {
                    String bookTitle = bookService.findById(img.getBookId())
                            .map(b -> b.title()).orElse("-");
                    Map<String, Object> body = new HashMap<>();
                    body.put("imageUrl",      img.getImageUrl());
                    body.put("inputImageUrl", img.getInputImageUrl());
                    body.put("style",         img.getStyle());
                    body.put("bookId",        img.getBookId());
                    body.put("bookTitle",     bookTitle);
                    log.info("{}.getImageDetail End!", this.getClass().getName());
                    return body;
                })
                .orElse(null);
    }
    // 스타일 선택
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