// ImageController.java - 책 속 주인공 이미지 생성 3단계 흐름을 처리하는 컨트롤러
// Step 1: 사진 업로드 → Step 2: 스타일 선택 → Step 3: 결과 확인
// /image/** 경로는 LoginInterceptor 가 인증을 검사한다
package com.from.controller;

import com.from.dto.ImageDetailDto;   // 이미지 상세 정보 DTO
import com.from.dto.ImageRequestDto;  // 이미지 생성 요청 DTO
import com.from.dto.ImageResultDto;   // 이미지 생성 결과 DTO
import com.from.dto.MsgDto;           // 공통 응답 메시지 DTO (result + msg)
import com.from.dto.BookSearchDto;    // 도서 검색 결과 DTO
import com.from.service.IBookService;  // 도서 비즈니스 로직 서비스 인터페이스
import com.from.service.IImageService; // 이미지 생성 서비스 인터페이스
import jakarta.servlet.http.HttpServletResponse; // 파일 다운로드용 HTTP 응답 객체
import jakarta.servlet.http.HttpSession;         // HTTP 세션 (로그인 사용자 확인용)
import lombok.RequiredArgsConstructor;           // final 필드 기반 생성자 자동 생성
import lombok.extern.slf4j.Slf4j;               // SLF4J 로거 자동 생성
import org.springframework.http.ResponseEntity;  // HTTP 상태코드 포함 응답 객체
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;             // 템플릿에 데이터 전달용
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // 파일 업로드 처리

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j                      // log.info(), log.error() 등 로거 사용 가능하게 해주는 어노테이션
@Controller                 // Spring MVC 컨트롤러로 등록 (View 이름 반환 가능)
@RequestMapping("/image")   // 이 컨트롤러의 모든 URL은 /image 로 시작
@RequiredArgsConstructor    // final 필드를 인자로 받는 생성자 자동 생성 (의존성 주입)
public class ImageController {

    private final IImageService imageService; // 이미지 생성 및 저장 서비스
    private final IBookService bookService;   // 도서 DB 처리 서비스

    // [GET] /image/create - Step 1: 사진 업로드 화면 반환
    // 유저의 등록 책 목록을 Model 에 담아 책 선택 드롭다운에 사용
    @GetMapping("/create")
    public String uploadPage(Model model, HttpSession session) {
        log.info("{}.uploadPage Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID"); // 세션에서 로그인 사용자 ID 추출
        if (userId == null) return "redirect:/user/login"; // 비로그인 시 로그인 페이지로 이동

        List<BookSearchDto> userBooks = bookService.findByUserId(userId); // 책 선택 드롭다운에 표시할 유저의 등록 책 목록 조회
        model.addAttribute("userBooks", userBooks); // 템플릿에 책 목록 전달

        log.info("{}.uploadPage End!", this.getClass().getName());
        return "image/step1-upload"; // templates/image/step1-upload.html 반환
    }

    // [POST] /image/create - Step 1 → Step 2: 업로드한 사진을 세션에 저장하고 스타일 선택 화면으로 이동
    // MultipartFile 을 byte[] 로 변환하여 세션에 저장함으로써 직렬화 문제를 방지
    @PostMapping("/create")
    @ResponseBody // 반환값을 JSON으로 직렬화하여 응답 body에 담음
    public ResponseEntity<?> uploadPhoto(
            //<?>는 반환 타입이 여려개 라서 타입이 미정
            @RequestParam("photo")     MultipartFile photo,     // 업로드한 사진 파일
            @RequestParam("bookId")    Long bookId,             // 선택한 책 ID
            @RequestParam("bookTitle") String bookTitle,        // 선택한 책 제목
            HttpSession session) {

        log.info("{}.uploadPhoto Start!", this.getClass().getName());

        if (photo.isEmpty()) { // 파일이 비어있으면 에러 반환
            return ResponseEntity.badRequest()
                    .body(MsgDto.builder().result(0).msg("사진을 선택해주세요.").build());
        }

        try {
            session.setAttribute("uploadedPhoto",     photo.getBytes());      // 사진을 byte[] 로 변환하여 세션에 저장
            session.setAttribute("uploadedPhotoType", photo.getContentType()); // 사진 MIME 타입 세션에 저장
            session.setAttribute("imageBookId",       bookId);                // 선택한 책 ID 세션에 저장
            session.setAttribute("imageBookTitle",    bookTitle);             // 선택한 책 제목 세션에 저장
        } catch (Exception e) {
            log.error("사진 업로드 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(MsgDto.builder().result(0).msg("사진 업로드에 실패했습니다.").build());
        }

        log.info("{}.uploadPhoto End!", this.getClass().getName());
        return ResponseEntity.ok(MsgDto.builder().result(1).msg("ok").build()); // 업로드 성공 응답
    }

    // [GET] /image/style - Step 2: 아트 스타일 선택 화면 반환
    // 선택 가능한 스타일 목록과 책 제목을 Model 에 담아 전달
    @GetMapping("/style")
    public String stylePage(HttpSession session, Model model) {
        log.info("{}.stylePage Start!", this.getClass().getName());

        if (session.getAttribute("SS_USER_ID") == null)    return "redirect:/user/login";    // 비로그인 시 로그인 페이지로 이동
        if (session.getAttribute("uploadedPhoto") == null) return "redirect:/image/create";  // 사진 업로드 없이 직접 접근하면 Step 1 으로 되돌림

        model.addAttribute("bookTitle",    session.getAttribute("imageBookTitle")); // 템플릿에 책 제목 전달
        model.addAttribute("styleOptions", imageService.getStyleOptions());         // 템플릿에 선택 가능한 스타일 목록 전달

        log.info("{}.stylePage End!", this.getClass().getName());
        return "image/step2-style"; // templates/image/step2-style.html 반환
    }

    // [POST] /image/generate - 이미지 생성 API (step2-style.html 에서 비동기 fetch 호출)
    // Claude → NanoBanana 순서로 호출하고 결과를 DB에 저장
    // 성공 시 {success:true, imageId:...} 반환, 실패 시 {success:false, errorMessage:...} 반환
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<?> generate(
            @RequestParam("scene") String scene, // 사용자가 입력한 장면 설명 텍스트
            @RequestParam("style") String style, // 선택한 아트 스타일 (watercolor, cartoon 등)
            HttpSession session) {

        log.info("{}.generate Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) { // 비로그인 시 401 반환
            return ResponseEntity.status(401)
                    .body(MsgDto.builder().result(0).msg("로그인이 필요합니다.").build());
        }

        // 세션에서 Step 1 에서 저장한 사진·책 정보 꺼내기
        byte[] photoBytes = (byte[]) session.getAttribute("uploadedPhoto");    // 업로드된 사진 바이트 배열
        String photoType  = (String) session.getAttribute("uploadedPhotoType"); // 사진 MIME 타입
        Long   bookId     = (Long)   session.getAttribute("imageBookId");       // 선택한 책 ID
        String bookTitle  = (String) session.getAttribute("imageBookTitle");    // 선택한 책 제목

        if (photoBytes == null) { // 사진이 세션에 없으면 에러 반환 (Step 1 미완료)
            return ResponseEntity.badRequest()
                    .body(MsgDto.builder().result(0).msg("사진을 먼저 업로드해주세요.").build());
        }

        try {
            ImageRequestDto request = new ImageRequestDto(bookId, bookTitle, scene, style); // 이미지 생성 요청 DTO 구성

            ImageResultDto result = imageService.generateAndSave(photoBytes, photoType, request, userId); // Claude → NanoBanana 이미지 생성 (실패 시 RuntimeException 발생)

            session.setAttribute("imageGeneratedPrompt",  result.generatedPrompt());  // 결과 화면에서 표시할 Claude 생성 프롬프트를 세션에 저장
            session.setAttribute("imageSceneDescription", result.sceneDescription()); // 결과 화면에서 표시할 장면 설명을 세션에 저장

            log.info("{}.generate End! - imageId:{}", this.getClass().getName(), result.imageId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imageId", result.imageId() // 생성된 이미지 ID 반환
            ));

        } catch (Exception e) {
            log.error("이미지 생성 실패: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "errorMessage", "이미지 생성에 실패했습니다. 잠시 후 다시 시도해주세요."
            ));
        }
    }

    // [GET] /image/result/{imageId} - Step 3: 이미지 결과 화면 반환
    // 세션에 저장된 Claude 프롬프트와 장면 설명도 모델에 담아 결과 화면에서 표시
    @GetMapping("/result/{imageId}")
    public String resultPage(
            @PathVariable Long imageId, // URL 경로에서 이미지 ID 추출
            Model model,
            HttpSession session) {

        log.info("{}.resultPage Start! - imageId:{}", this.getClass().getName(), imageId);

        model.addAttribute("imageId",          imageId);                                     // 템플릿에 이미지 ID 전달
        model.addAttribute("generatedPrompt",  session.getAttribute("imageGeneratedPrompt")); // Claude 가 생성한 프롬프트 전달
        model.addAttribute("sceneDescription", session.getAttribute("imageSceneDescription")); // 사용자가 입력한 장면 설명 전달

        log.info("{}.resultPage End!", this.getClass().getName());
        return "image/step3-result"; // templates/image/step3-result.html 반환
    }

    // [GET] /image/download/{imageId} - S3 이미지를 서버 사이드에서 프록시하여 파일 다운로드로 반환
    // 브라우저에서 S3 URL 직접 fetch 시 CORS 문제로 새 탭이 열리는 것을 방지
    @GetMapping("/download/{imageId}")
    public void downloadImage(
            @PathVariable Long imageId,      // 다운로드할 이미지 ID
            HttpServletResponse response) {  // 파일 스트리밍용 HTTP 응답 객체

        log.info("{}.downloadImage Start! - imageId:{}", this.getClass().getName(), imageId);
        try {
            ImageDetailDto detail = imageService.getImageDetail(imageId); // 이미지 상세 정보 조회
            if (detail == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; } // 이미지 없으면 404 반환

            String imageUrl  = detail.imageUrl(); // S3 이미지 URL
            String bookTitle = detail.bookTitle() != null ? detail.bookTitle() : "from-character"; // 책 제목 (없으면 기본값)
            String safeTitle = URLEncoder.encode(bookTitle + ".png", StandardCharsets.UTF_8)
                    .replace("+", "%20"); // 파일명 URL 인코딩 (한글 파일명 깨짐 방지)

            try (InputStream in = new URL(imageUrl).openStream()) { // S3 URL 에서 이미지 스트림 열기
                response.setContentType("image/png"); // 응답 Content-Type 을 PNG 이미지로 설정
                response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + safeTitle); // 브라우저가 파일 저장 다이얼로그를 열도록 설정
                in.transferTo(response.getOutputStream()); // S3 이미지 스트림을 응답 스트림으로 그대로 전달
            }
            log.info("{}.downloadImage End!", this.getClass().getName());
        } catch (Exception e) {
            log.error("이미지 다운로드 실패 - imageId:{}", imageId, e);
        }
    }

    // [GET] /image/detail/{imageId} - 특정 이미지 결과의 상세 정보를 JSON 으로 반환
    // 반환: {imageUrl, style, bookId} 또는 404
    @GetMapping("/detail/{imageId}")
    @ResponseBody
    public ResponseEntity<?> getDetail(@PathVariable Long imageId) { // URL 경로에서 이미지 ID 추출
        log.info("{}.getDetail Start! - imageId:{}", this.getClass().getName(), imageId);

        ImageDetailDto detail = imageService.getImageDetail(imageId); // 이미지 상세 정보 조회

        log.info("{}.getDetail End!", this.getClass().getName());
        return detail != null
                ? ResponseEntity.ok(detail)              // 조회 성공 시 상세 정보 반환
                : ResponseEntity.notFound().build();     // 없으면 404 반환
    }

    // [GET] /image/my - 현재 유저의 이미지 생성 히스토리를 JSON 으로 반환
    @GetMapping("/my")
    @ResponseBody
    public ResponseEntity<?> myImages(HttpSession session) {
        log.info("{}.myImages Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(MsgDto.builder().result(0).msg("로그인이 필요합니다.").build()); // 비로그인 시 401 반환

        ResponseEntity<?> response = ResponseEntity.ok(imageService.getUserImages(userId)); // 유저의 이미지 히스토리 조회
        log.info("{}.myImages End!", this.getClass().getName());
        return response;
    }

    // [POST] /image/generate-direct - SPA용 단일 요청 이미지 생성 API
    // 사진·책·스타일을 한 번에 받아 Claude + 이미지 생성 API를 호출하고 결과를 반환
    // review/image.html 의 클라이언트 사이드 3단계 플로우에서 사용
    @PostMapping("/generate-direct")
    @ResponseBody
    public ResponseEntity<?> generateDirect(
            @RequestParam("photo")                                  MultipartFile photo,     // 업로드한 사진 파일
            @RequestParam("bookTitle")                              String bookTitle,        // 책 제목
            @RequestParam("style")                                  String style,            // 아트 스타일
            @RequestParam(value = "bookId", required = false,
                    defaultValue = "0")                       Long bookId,             // 책 ID (선택값, 없으면 0)
            HttpSession session) {

        log.info("{}.generateDirect Start! - bookTitle:{}, style:{}", this.getClass().getName(), bookTitle, style);

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).body(MsgDto.builder().result(0).msg("로그인이 필요합니다.").build()); // 비로그인 시 401 반환

        if (photo.isEmpty()) return ResponseEntity.badRequest().body(MsgDto.builder().result(0).msg("사진을 선택해주세요.").build()); // 파일이 비어있으면 에러 반환

        try {
            String scene = "책 '" + bookTitle + "'의 주인공으로 등장하는 장면"; // 장면 설명 자동 생성
            ImageRequestDto request = new ImageRequestDto(bookId, bookTitle, scene, style); // 이미지 생성 요청 DTO 구성
            ImageResultDto result =
                    imageService.generateAndSave(photo.getBytes(), photo.getContentType(), request, userId); // Claude → 이미지 생성 API 호출 후 DB 저장
            log.info("{}.generateDirect End! - imageId:{}", this.getClass().getName(), result.imageId());
            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "imageId",  result.imageId(),  // 생성된 이미지 ID
                    "imageUrl", result.imageUrl()  // 생성된 이미지 URL
            ));
        } catch (Exception e) {
            log.error("이미지 직접 생성 오류", e);
            return ResponseEntity.ok(Map.of(
                    "success",      false,
                    "errorMessage", "이미지 생성에 실패했습니다. 잠시 후 다시 시도해주세요."
            ));
        }
    }
}