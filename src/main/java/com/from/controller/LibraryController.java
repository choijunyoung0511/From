// LibraryController.java - 도서관 정보나루 Open API 기반 인기대출도서 화면
package com.from.controller;

import com.from.constant.RegionCode;
import com.from.dto.LibraryBookAvailabilityDto;
import com.from.dto.LibraryBookSearchDto;
import com.from.dto.LibraryDto;
import com.from.dto.MsgDto;
import com.from.service.ILibraryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/library")
@RequiredArgsConstructor
public class LibraryController {

    private final ILibraryService libraryService;

    // [GET] /library - 도서관 소장 확인 메인 페이지
    // 책 검색 → 책 선택 → 지역 선택 → 소장 도서관 조회 → 대출가능여부 확인까지 한 페이지에서 단계적으로 처리한다
    // FROM DB의 BookEntity/등록된 책과 무관한 독립 조회 기능 - 각 단계는 GET 쿼리 파라미터로 이어간다 (DB 저장 없음)
    @GetMapping
    public String main(
            @RequestParam(required = false, defaultValue = "") String title,
            @RequestParam(required = false, defaultValue = "") String isbn13,
            @RequestParam(required = false, defaultValue = "") String bookName,
            @RequestParam(required = false, defaultValue = "") String bookAuthor,
            @RequestParam(required = false, defaultValue = "") String bookPublisher,
            @RequestParam(required = false, defaultValue = "") String region,
            @RequestParam(required = false, defaultValue = "") String libCode,
            @RequestParam(required = false, defaultValue = "") String libName,
            @RequestParam(required = false, defaultValue = "") String libAddress,
            Model model) {

        log.info("{}.main Start! - title:{}, isbn13:{}, region:{}, libCode:{}",
                this.getClass().getName(), title, isbn13, region, libCode);

        model.addAttribute("title", title);
        model.addAttribute("isbn13", isbn13);
        model.addAttribute("bookName", bookName);
        model.addAttribute("bookAuthor", bookAuthor);
        model.addAttribute("bookPublisher", bookPublisher);
        model.addAttribute("region", region);
        model.addAttribute("regions", RegionCode.values());

        // STEP 1: 책 검색 - 제목만 입력되고 아직 책을 선택하지 않은 경우에만 검색 실행
        if (!title.isBlank() && isbn13.isBlank()) {
            List<LibraryBookSearchDto> books = libraryService.searchBooks(title);
            if (books == null) {
                model.addAttribute("searchError", "도서 검색에 실패했습니다. 잠시 후 다시 시도해주세요.");
            } else if (books.isEmpty()) {
                model.addAttribute("searchError", "검색 결과가 없습니다.");
            } else {
                model.addAttribute("searchResults", books);
            }
        }

        // STEP 2: 소장 도서관 조회 - 책(isbn13) + 지역이 모두 선택된 경우
        if (!isbn13.isBlank() && !region.isBlank()) {
            String regionCode = RegionCode.codeOf(region);
            if (regionCode == null) {
                model.addAttribute("holdingsError", "지역을 다시 선택해주세요.");
            } else {
                List<LibraryDto> libraries = libraryService.findLibrariesByBook(isbn13, regionCode);
                if (libraries == null) {
                    model.addAttribute("holdingsError", "현재 도서관 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");
                } else if (libraries.isEmpty()) {
                    model.addAttribute("holdingsError", "선택한 지역에서 이 책을 소장한 도서관을 찾지 못했습니다.");
                } else {
                    model.addAttribute("libraries", libraries);
                }
            }
        }

        // STEP 3: 대출 가능 여부 확인 - 도서관(libCode)까지 선택된 경우
        if (!isbn13.isBlank() && !libCode.isBlank()) {
            model.addAttribute("selectedLibName", libName);
            model.addAttribute("selectedLibAddress", libAddress);

            LibraryBookAvailabilityDto availability = libraryService.checkBookAvailability(libCode, isbn13);
            if (availability == null) {
                model.addAttribute("availabilityError", "현재 대출 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");
            } else {
                model.addAttribute("availability", availability);
            }
        }

        log.info("{}.main End!", this.getClass().getName());
        return "library/main";
    }

    // [GET] /library/ranking - 인기대출도서 페이지
    @GetMapping("/ranking")
    public String ranking(Model model) {
        log.info("{}.ranking Start!", this.getClass().getName());

        model.addAttribute("popularBooks", libraryService.getPopularBooks());

        log.info("{}.ranking End!", this.getClass().getName());
        return "library/ranking";
    }

    // [GET] /library/search - 도서관명으로 도서관 검색 (JSON). libCode는 응답에 포함되지만
    // 화면에는 노출하지 않고, 사용자가 도서관을 선택했을 때 내부적으로만 사용한다
    // 정보나루 도서관 목록 API 자체가 불안정할 수 있어 실패(null)와 "일치하는 도서관 없음"(빈 리스트)을 구분해 응답한다
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<?> searchLibraries(@RequestParam(required = false, defaultValue = "") String libName) {
        log.info("{}.searchLibraries Start! - libName:{}", this.getClass().getName(), libName);
        List<LibraryDto> result = libraryService.searchLibraries(libName);

        if (result == null) {
            log.warn("{}.searchLibraries End! - 조회 실패", this.getClass().getName());
            return ResponseEntity.status(502)
                    .body(MsgDto.builder().result(0).msg("도서관 검색에 실패했습니다. 잠시 후 다시 시도해주세요.").build());
        }

        log.info("{}.searchLibraries End! - {}건", this.getClass().getName(), result.size());
        return ResponseEntity.ok(result);
    }

    // [GET] /library/book-availability - 도서관 소장/대출가능 여부 조회 페이지
    // isbn13/title/author는 책 검색·베스트셀러·마이페이지에서 전달받아 폼에 미리 채워줌 (새로 입력하지 않아도 되도록)
    // libCode는 사용자가 직접 입력하지 않고, 도서관명 검색 후 선택하면 화면에서 hidden 값으로 채워져 전달됨
    // libName/libAddress는 결과 화면 표시용으로만 사용 (조회 로직에는 사용하지 않음)
    // submitted=1일 때만 조회를 실행하고, 그 전에는 검색 폼만 보여준다
    @GetMapping("/book-availability")
    public String bookAvailability(
            @RequestParam(required = false, defaultValue = "") String isbn13,
            @RequestParam(required = false, defaultValue = "") String libCode,
            @RequestParam(required = false, defaultValue = "") String libName,
            @RequestParam(required = false, defaultValue = "") String libAddress,
            @RequestParam(required = false, defaultValue = "") String title,
            @RequestParam(required = false, defaultValue = "") String author,
            @RequestParam(required = false, defaultValue = "") String submitted,
            Model model) {

        log.info("{}.bookAvailability Start! - isbn13:{}, libCode:{}", this.getClass().getName(), isbn13, libCode);

        model.addAttribute("isbn13", isbn13);
        model.addAttribute("bookTitle", title);
        model.addAttribute("bookAuthor", author);
        model.addAttribute("libName", libName);
        model.addAttribute("libAddress", libAddress);

        if ("1".equals(submitted)) {
            if (isbn13.isBlank()) {
                model.addAttribute("errorMessage", "ISBN13이 필요합니다.");
            } else if (libCode.isBlank()) {
                model.addAttribute("errorMessage", "도서관을 선택해주세요.");
            } else {
                LibraryBookAvailabilityDto availability = libraryService.checkBookAvailability(libCode, isbn13);
                if (availability == null) {
                    model.addAttribute("errorMessage", "도서관 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
                } else {
                    model.addAttribute("availability", availability);
                }
            }
        }

        log.info("{}.bookAvailability End!", this.getClass().getName());
        return "library/book-availability";
    }
}
