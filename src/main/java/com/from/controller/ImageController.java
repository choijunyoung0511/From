//package com.from.controller;
//
//import com.from.domain.Book;
//import com.from.dto.ImageDto;
//import com.from.dto.user.SessionUser;
//import com.from.mapper.BookMapper;
//import com.from.repository.ImageResultRepository;
//import com.from.service.ImageService;
//import jakarta.servlet.http.HttpSession;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//import java.util.Map;
//
//@Controller
//@RequestMapping("/image")
//@RequiredArgsConstructor
//public class ImageController {
//
//    private final ImageService imageService;
//    private final ImageResultRepository imageResultRepository;
//    private final BookMapper bookMapper;
//
//    // ── Step 1: 사진 업로드 화면 ─────────────────────────────────────────
//    @GetMapping("/create")
//    public String uploadPage(Model model, HttpSession session) {
//        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
//        if (loginUser == null) return "redirect:/user/login";
//
//        List<Book> userBooks = bookMapper.findBooksByUserId(loginUser.getUserId());
//        model.addAttribute("userBooks", userBooks);
//        return "image/step1-upload";
//    }
//
//    // Step 1 → Step 2: byte[]로 세션 저장
//    @PostMapping("/create")
//    public String uploadPhoto(@RequestParam("photo") MultipartFile photo,
//                              @RequestParam("bookId") Long bookId,
//                              @RequestParam("bookTitle") String bookTitle,
//                              HttpSession session, Model model) {
//        if (photo.isEmpty()) {
//            model.addAttribute("error", "사진을 선택해주세요.");
//            return "image/step1-upload";
//        }
//        try {
//            // MultipartFile 대신 byte[]로 저장 (세션 직렬화 문제 방지)
//            session.setAttribute("uploadedPhoto", photo.getBytes());
//            session.setAttribute("uploadedPhotoType", photo.getContentType());
//            session.setAttribute("bookId", bookId);
//            session.setAttribute("bookTitle", bookTitle);
//        } catch (Exception e) {
//            model.addAttribute("error", "사진 업로드에 실패했습니다.");
//            return "image/step1-upload";
//        }
//        return "redirect:/image/style";
//    }
//
//    // ── Step 2: 스타일 선택 화면 ─────────────────────────────────────────
//    @GetMapping("/style")
//    public String stylePage(HttpSession session, Model model) {
//        if (session.getAttribute("loginUser") == null) return "redirect:/user/login";
//        if (session.getAttribute("uploadedPhoto") == null) return "redirect:/image/create";
//
//        model.addAttribute("bookTitle", session.getAttribute("bookTitle"));
//        model.addAttribute("styleOptions", imageService.getStyleOptions());
//        return "image/step2-style";
//    }
//
//    // ── 이미지 생성 (비동기 API) ──────────────────────────────────────────
//    @PostMapping("/generate")
//    @ResponseBody
//    public ResponseEntity<ImageDto.GenerateResponse> generate(
//            @RequestParam("scene") String scene,
//            @RequestParam("style") String style,
//            HttpSession session) {
//
//        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
//        if (loginUser == null) return ResponseEntity.status(401).build();
//
//        byte[] photoBytes = (byte[]) session.getAttribute("uploadedPhoto");
//        String photoType  = (String) session.getAttribute("uploadedPhotoType");
//        Long bookId       = (Long)   session.getAttribute("bookId");
//        String bookTitle  = (String) session.getAttribute("bookTitle");
//
//        if (photoBytes == null) return ResponseEntity.badRequest().build();
//
//        ImageDto.GenerateRequest request = new ImageDto.GenerateRequest();
//        request.setBookId(bookId);
//        request.setBookTitle(bookTitle);
//        request.setScene(scene);
//        request.setStyle(style);
//
//        ImageDto.GenerateResponse response = imageService.generateAndSave(
//                photoBytes, photoType, request, loginUser.getUserId());
//        return ResponseEntity.ok(response);
//    }
//
//    // ── 결과 화면 ────────────────────────────────────────────────────────
//    @GetMapping("/result/{imageId}")
//    public String resultPage(@PathVariable Long imageId, Model model) {
//        model.addAttribute("imageId", imageId);
//        return "image/step3-result";
//    }
//
//    // 결과 상세 API
//    @GetMapping("/detail/{imageId}")
//    @ResponseBody
//    public ResponseEntity<?> getDetail(@PathVariable Long imageId) {
//        return imageResultRepository.findById(imageId)
//                .map(img -> ResponseEntity.ok(Map.of(
//                        "imageUrl", img.getImageUrl(),
//                        "style",    img.getStyle(),
//                        "bookId",   img.getBookId()
//                )))
//                .orElse(ResponseEntity.notFound().build());
//    }
//
//    // 내 이미지 목록 (마이페이지 연동)
//    @GetMapping("/my")
//    @ResponseBody
//    public ResponseEntity<?> myImages(HttpSession session) {
//        SessionUser loginUser = (SessionUser) session.getAttribute("loginUser");
//        if (loginUser == null) return ResponseEntity.status(401).build();
//        return ResponseEntity.ok(imageService.getUserImages(loginUser.getUserId()));
//    }
//}