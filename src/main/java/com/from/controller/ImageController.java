package com.from.controller;

import com.from.dto.ImageDto;
import com.from.repository.ImageResultRepository;
import com.from.repository.entity.BookEntity;
import com.from.service.IBookService;
import com.from.service.IImageService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 책 속 주인공 이미지 생성 3단계 흐름을 처리하는 컨트롤러.
 * Step 1: 사진 업로드 → Step 2: 스타일 선택 → Step 3: 결과 확인
 * /image/** 경로는 LoginInterceptor 가 인증을 검사한다.
 */
@Slf4j
@Controller
@RequestMapping("/image")
@RequiredArgsConstructor
public class ImageController {

    private final IImageService imageService;
    private final ImageResultRepository imageResultRepository;
    private final IBookService bookService;

    /**
     * Step 1: 사진 업로드 화면을 반환한다.
     * 유저의 등록 책 목록을 Model 에 담아 책 선택 드롭다운에 사용한다.
     *
     * @param model   책 목록(userBooks) 전달용
     * @param session 로그인 세션
     * @return image/step1-upload 템플릿
     */
    @GetMapping("/create")
    public String uploadPage(Model model, HttpSession session) {
        log.info("{}.uploadPage Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        // 책 선택 드롭다운에 표시할 유저의 등록 책 목록
        List<BookEntity> userBooks = bookService.findByUserId(userId);
        model.addAttribute("userBooks", userBooks);

        log.info("{}.uploadPage End!", this.getClass().getName());
        return "image/step1-upload";
    }

    /**
     * Step 1 → Step 2: 업로드한 사진을 세션에 저장하고 스타일 선택 화면으로 이동한다.
     * MultipartFile 을 byte[] 로 변환하여 세션에 저장함으로써 직렬화 문제를 방지한다.
     *
     * @param photo     업로드한 사진 파일
     * @param bookId    선택한 책 ID
     * @param bookTitle 선택한 책 제목
     * @param session   로그인 세션
     * @param model     에러 메시지 전달용
     * @return Step 2 화면으로 리다이렉트
     */
    @PostMapping("/create")
    public String uploadPhoto(@RequestParam("photo")     MultipartFile photo,
                              @RequestParam("bookId")    Long bookId,
                              @RequestParam("bookTitle") String bookTitle,
                              HttpSession session, Model model) {
        log.info("{}.uploadPhoto Start!", this.getClass().getName());

        if (photo.isEmpty()) {
            model.addAttribute("error", "사진을 선택해주세요.");
            return "image/step1-upload";
        }

        try {
            // 세션에 byte[] 로 저장 (MultipartFile 은 세션 직렬화 불가)
            session.setAttribute("uploadedPhoto",     photo.getBytes());
            session.setAttribute("uploadedPhotoType", photo.getContentType());
            session.setAttribute("bookId",            bookId);
            session.setAttribute("bookTitle",         bookTitle);
        } catch (Exception e) {
            log.error("사진 업로드 실패: {}", e.getMessage());
            model.addAttribute("error", "사진 업로드에 실패했습니다.");
            return "image/step1-upload";
        }

        log.info("{}.uploadPhoto End!", this.getClass().getName());
        return "redirect:/image/style";
    }

    /**
     * Step 2: 아트 스타일 선택 화면을 반환한다.
     * 선택 가능한 스타일 목록과 책 제목을 Model 에 담는다.
     *
     * @param session 로그인 세션
     * @param model   스타일 목록(styleOptions), 책 제목(bookTitle) 전달용
     * @return image/step2-style 템플릿
     */
    @GetMapping("/style")
    public String stylePage(HttpSession session, Model model) {
        log.info("{}.stylePage Start!", this.getClass().getName());

        // 세션 확인 (사진 업로드 없이 직접 접근하면 Step 1 으로 되돌림)
        if (session.getAttribute("SS_USER_ID") == null)     return "redirect:/user/login";
        if (session.getAttribute("uploadedPhoto") == null) return "redirect:/image/create";

        model.addAttribute("bookTitle",    session.getAttribute("bookTitle"));
        model.addAttribute("styleOptions", imageService.getStyleOptions());

        log.info("{}.stylePage End!", this.getClass().getName());
        return "image/step2-style";
    }

    /**
     * 이미지 생성 API (비동기 호출).
     * 세션에 저장된 사진과 요청 파라미터를 ImageService 에 전달하여 Gemini API 를 호출한다.
     * 생성된 이미지는 DB에 저장되고 응답으로 imageId·imageUrl 을 반환한다.
     *
     * @param scene   장면 설명 텍스트
     * @param style   아트 스타일 (watercolor, cartoon 등)
     * @param session 로그인 세션
     * @return {@link ImageDto.GenerateResponse} JSON
     */
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<ImageDto.GenerateResponse> generate(
            @RequestParam("scene") String scene,
            @RequestParam("style") String style,
            HttpSession session) {

        log.info("{}.generate Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).build();

        // 세션에서 Step 1 에서 저장한 사진·책 정보 꺼내기
        byte[] photoBytes = (byte[]) session.getAttribute("uploadedPhoto");
        String photoType  = (String) session.getAttribute("uploadedPhotoType");
        Long   bookId     = (Long)   session.getAttribute("bookId");
        String bookTitle  = (String) session.getAttribute("bookTitle");

        if (photoBytes == null) return ResponseEntity.badRequest().build();

        // 이미지 생성 요청 DTO 구성
        ImageDto.GenerateRequest request = new ImageDto.GenerateRequest(bookId, bookTitle, scene, style);

        ImageDto.GenerateResponse response =
                imageService.generateAndSave(photoBytes, photoType, request, userId);

        log.info("{}.generate End!", this.getClass().getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Step 3: 이미지 결과 화면을 반환한다.
     *
     * @param imageId 생성된 이미지 ID
     * @param model   imageId 전달용
     * @return image/step3-result 템플릿
     */
    @GetMapping("/result/{imageId}")
    public String resultPage(@PathVariable Long imageId, Model model) {
        log.info("{}.resultPage Start! - imageId:{}", this.getClass().getName(), imageId);
        model.addAttribute("imageId", imageId);
        log.info("{}.resultPage End!", this.getClass().getName());
        return "image/step3-result";
    }

    /**
     * 특정 이미지 결과의 상세 정보를 JSON 으로 반환한다.
     *
     * @param imageId 조회할 이미지 ID
     * @return {imageUrl, style, bookId} 또는 404
     */
    @GetMapping("/detail/{imageId}")
    @ResponseBody
    public ResponseEntity<?> getDetail(@PathVariable Long imageId) {
        log.info("{}.getDetail Start! - imageId:{}", this.getClass().getName(), imageId);

        ResponseEntity<?> response = imageResultRepository.findById(imageId)
                .map(img -> ResponseEntity.ok((Object) Map.of(
                        "imageUrl", img.getImageUrl(),
                        "style",    img.getStyle(),
                        "bookId",   img.getBookId()
                )))
                .orElse(ResponseEntity.notFound().build());

        log.info("{}.getDetail End!", this.getClass().getName());
        return response;
    }

    /**
     * 현재 유저의 이미지 생성 히스토리를 JSON 으로 반환한다.
     *
     * @param session 로그인 세션
     * @return 이미지 결과 목록 또는 401
     */
    @GetMapping("/my")
    @ResponseBody
    public ResponseEntity<?> myImages(HttpSession session) {
        log.info("{}.myImages Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return ResponseEntity.status(401).build();

        ResponseEntity<?> response = ResponseEntity.ok(imageService.getUserImages(userId));
        log.info("{}.myImages End!", this.getClass().getName());
        return response;
    }
}
