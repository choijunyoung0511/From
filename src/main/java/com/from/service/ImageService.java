//package com.from.service;
//
//import com.from.dto.ImageDto;
//import com.from.domain.ImageResult;
//import com.from.repository.ImageResultRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.Arrays;
//import java.util.List;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class ImageService {
//
//    private final NanobanaService nanobanaService;
//    private final ImageResultRepository imageResultRepository;
//
//    @Transactional
//    public ImageDto.GenerateResponse generateAndSave(
//            byte[] photoBytes,
//            String photoType,
//            ImageDto.GenerateRequest request,
//            Long userId) {
//        try {
//            log.info("이미지 생성 시작 - userId:{}, bookId:{}, style:{}",
//                    userId, request.getBookId(), request.getStyle());
//
//            // 나노바나나(Gemini) API 호출
//            String imageUrl = nanobanaService.generateImage(
//                    photoBytes,
//                    photoType,
//                    request.getScene(),
//                    request.getStyle(),
//                    request.getBookTitle()
//            );
//
//            // DB 저장
//            ImageResult saved = imageResultRepository.save(ImageResult.builder()
//                    .userId(userId)
//                    .bookId(request.getBookId())
//                    .imageUrl(imageUrl)
//                    .style(request.getStyle())
//                    .build());
//
//            log.info("이미지 생성 완료 - imageId:{}", saved.getImageId());
//
//            return ImageDto.GenerateResponse.builder()
//                    .imageId(saved.getImageId())
//                    .imageUrl(imageUrl)
//                    .style(request.getStyle())
//                    .bookTitle(request.getBookTitle())
//                    .success(true)
//                    .build();
//
//        } catch (Exception e) {
//            log.error("이미지 생성 실패: {}", e.getMessage());
//            return ImageDto.GenerateResponse.builder()
//                    .success(false)
//                    .errorMessage(e.getMessage())
//                    .build();
//        }
//    }
//
//    public List<ImageResult> getUserImages(Long userId) {
//        return imageResultRepository.findByUserIdOrderByCreateAtDesc(userId);
//    }
//
//    public List<ImageDto.StyleOption> getStyleOptions() {
//        return Arrays.asList(
//                new ImageDto.StyleOption("watercolor",   "수채화",       "부드럽고 몽환적인 수채화 스타일"),
//                new ImageDto.StyleOption("cartoon",      "만화/애니메이션", "생동감 있는 애니메이션 스타일"),
//                new ImageDto.StyleOption("realistic",    "사실적",       "실제 사진처럼 정교한 스타일"),
//                new ImageDto.StyleOption("oil_painting", "유화",         "고전 명화 같은 유화 스타일"),
//                new ImageDto.StyleOption("sketch",       "스케치",       "연필 드로잉 스타일"),
//                new ImageDto.StyleOption("fantasy",      "판타지",       "동화 속 환상적인 스타일")
//        );
//    }
//}