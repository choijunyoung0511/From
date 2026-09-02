package com.from.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.from.dto.LibraryBookAvailabilityDto;
import com.from.dto.LibraryBookSearchDto;
import com.from.dto.LibraryDto;
import com.from.dto.PopularBookDto;
import com.from.service.ILibraryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// 도서관 정보나루 Open API(data4library.kr) - 인기대출도서/도서관 검색/소장여부 조회
// WeatherService와 동일한 패턴: WebClient로 호출 → Jackson으로 JSON 트리 파싱 → DTO 변환
@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryService implements ILibraryService {

    // 정보나루 인증키. 발급 전(빈 값)이어도 애플리케이션은 정상 기동되어야 하므로 기본값을 빈 문자열로 둔다
    @Value("${library.api.key:}")
    private String apiKey;

    @Value("${library.api.base-url:https://data4library.kr/api}")
    private String baseUrl;

    // 인기대출도서 최대 노출 권수
    private static final int POPULAR_LIMIT = 10;

    // 도서관 검색 결과 최대 노출 개수
    private static final int LIBRARY_SEARCH_LIMIT = 30;

    // 도서 검색 결과 최대 노출 권수
    private static final int BOOK_SEARCH_LIMIT = 10;

    // 소장 도서관 조회 결과 최대 노출 개수
    private static final int HOLDINGS_LIMIT = 20;

    // 도서관 목록 캐시 (전국 도서관 수가 많고 자주 바뀌지 않아 AladinService 베스트셀러와 동일하게 Redis 캐싱 - libSrch API가 도서관명 검색을 직접 지원하지 않아 전체 목록을 받아 서버에서 이름으로 필터링한다)
    private static final String LIBRARY_LIST_CACHE_KEY = "library:all";
    private static final Duration LIBRARY_LIST_CACHE_TTL = Duration.ofHours(24);

    // libSrch API 1회 조회 건수 (정보나루 서버가 대량 조회 시 504/502를 자주 반환해 작게 유지)
    private static final int LIBRARY_PAGE_SIZE = 300;

    // libSrch 외부 API 응답 대기 한도. 정보나루 서버가 느릴 때 요청 스레드가 무한정 묶이지 않도록 제한한다
    private static final Duration LIBRARY_API_TIMEOUT = Duration.ofSeconds(12);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<PopularBookDto> getPopularBooks() {
        log.info("{}.getPopularBooks Start!", this.getClass().getName());

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("정보나루 API 인증키(library.api.key)가 설정되지 않아 인기대출도서 조회를 건너뜁니다.");
            return List.of();
        }

        List<PopularBookDto> result = new ArrayList<>();
        try {
            String json = WebClient.create(baseUrl).get()
                    .uri(b -> b.path("/loanItemSrch")
                            .queryParam("authKey", apiKey)
                            .queryParam("pageNo", 1)
                            .queryParam("pageSize", POPULAR_LIMIT)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(json);
            for (JsonNode entry : unwrapArray(root.path("response").path("docs"))) {
                JsonNode doc = unwrapSingle(entry, "doc");
                result.add(PopularBookDto.builder()
                        .ranking(doc.path("ranking").asInt(0))
                        .bookName(doc.path("bookname").asText(""))
                        .authors(doc.path("authors").asText(""))
                        .publisher(doc.path("publisher").asText(""))
                        .isbn13(doc.path("isbn13").asText(""))
                        .bookImageUrl(doc.path("bookImageURL").asText(""))
                        .loanCount(doc.path("loan_count").asInt(0))
                        .build());
                if (result.size() >= POPULAR_LIMIT) break;
            }
        } catch (Exception e) {
            log.error("인기대출도서 조회 오류", e);
            return List.of();
        }

        log.info("{}.getPopularBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    @Override
    public LibraryBookAvailabilityDto checkBookAvailability(String libCode, String isbn13) {
        log.info("{}.checkBookAvailability Start! - libCode:{}, isbn13:{}", this.getClass().getName(), libCode, isbn13);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("정보나루 API 인증키(library.api.key)가 설정되지 않아 소장 여부 조회를 건너뜁니다.");
            return null;
        }
        if (libCode == null || libCode.isBlank() || isbn13 == null || isbn13.isBlank()) {
            log.warn("libCode 또는 isbn13이 비어 있어 소장 여부 조회를 건너뜁니다.");
            return null;
        }

        try {
            String json = WebClient.create(baseUrl).get()
                    .uri(b -> b.path("/bookExist")
                            .queryParam("authKey", apiKey)
                            .queryParam("libCode", libCode)
                            .queryParam("isbn13", isbn13)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(LIBRARY_API_TIMEOUT)
                    .block();

            if (json == null || json.isBlank()) {
                log.warn("도서관 소장 여부 API 응답이 비어 있습니다.");
                return null;
            }

            JsonNode result = objectMapper.readTree(json).path("response").path("result");

            LibraryBookAvailabilityDto dto = LibraryBookAvailabilityDto.builder()
                    .libraryCode(libCode)
                    .isbn13(isbn13)
                    .hasBook("Y".equalsIgnoreCase(result.path("hasBook").asText("N")))
                    .loanAvailable("Y".equalsIgnoreCase(result.path("loanAvailable").asText("N")))
                    .build();

            log.info("{}.checkBookAvailability End! - hasBook:{}, loanAvailable:{}",
                    this.getClass().getName(), dto.hasBook(), dto.loanAvailable());
            return dto;

        } catch (Exception e) {
            log.error("도서관 소장 여부 조회 오류 - libCode:{}, isbn13:{}", libCode, isbn13, e);
            return null;
        }
    }

    // null = API 실패, 빈 리스트 = 검색 결과 없음
    @Override
    public List<LibraryBookSearchDto> searchBooks(String title) {
        log.info("{}.searchBooks Start! - title:{}", this.getClass().getName(), title);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("정보나루 API 인증키(library.api.key)가 설정되지 않아 도서 검색을 건너뜁니다.");
            return null;
        }
        if (title == null || title.isBlank()) {
            return List.of();
        }

        List<LibraryBookSearchDto> result = new ArrayList<>();
        try {
            String json = WebClient.create(baseUrl).get()
                    .uri(b -> b.path("/srchBooks")
                            .queryParam("authKey", apiKey)
                            .queryParam("title", title)
                            .queryParam("pageNo", 1)
                            .queryParam("pageSize", BOOK_SEARCH_LIMIT)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(LIBRARY_API_TIMEOUT)
                    .block();

            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            for (JsonNode entry : unwrapArray(root.path("response").path("docs"))) {
                JsonNode doc = unwrapSingle(entry, "doc");
                String isbn13 = doc.path("isbn13").asText("");
                if (isbn13.isBlank()) continue; // ISBN 없으면 다음 단계(소장 도서관 조회)에 쓸 수 없어 제외

                result.add(LibraryBookSearchDto.builder()
                        .bookName(doc.path("bookname").asText("").trim())
                        .authors(doc.path("authors").asText(""))
                        .publisher(doc.path("publisher").asText(""))
                        .publicationYear(doc.path("publication_year").asText(""))
                        .isbn13(isbn13)
                        .bookImageUrl(doc.path("bookImageURL").asText(""))
                        .build());
                if (result.size() >= BOOK_SEARCH_LIMIT) break;
            }
        } catch (Exception e) {
            log.error("도서 검색 오류 - title:{}", title, e);
            return null;
        }

        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    // null = API 실패, 빈 리스트 = 해당 지역에 소장 도서관 없음
    @Override
    public List<LibraryDto> findLibrariesByBook(String isbn13, String regionCode) {
        log.info("{}.findLibrariesByBook Start! - isbn13:{}, regionCode:{}", this.getClass().getName(), isbn13, regionCode);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("정보나루 API 인증키(library.api.key)가 설정되지 않아 소장 도서관 조회를 건너뜁니다.");
            return null;
        }
        if (isbn13 == null || isbn13.isBlank() || regionCode == null || regionCode.isBlank()) {
            return List.of();
        }

        List<LibraryDto> result = new ArrayList<>();
        try {
            // 매뉴얼상 libSrchByBook의 ISBN 파라미터명은 isbn13이 아니라 isbn
            String json = WebClient.create(baseUrl).get()
                    .uri(b -> b.path("/libSrchByBook")
                            .queryParam("authKey", apiKey)
                            .queryParam("isbn", isbn13)
                            .queryParam("region", regionCode)
                            .queryParam("pageNo", 1)
                            .queryParam("pageSize", HOLDINGS_LIMIT)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(LIBRARY_API_TIMEOUT)
                    .block();

            if (json == null || json.isBlank()) return null;

            JsonNode root = objectMapper.readTree(json);
            for (JsonNode entry : unwrapArray(root.path("response").path("libs"))) {
                JsonNode lib = unwrapSingle(entry, "lib");
                result.add(LibraryDto.builder()
                        .libCode(lib.path("libCode").asText(""))
                        .libName(lib.path("libName").asText(""))
                        .address(lib.path("address").asText(""))
                        .tel(lib.path("tel").asText(""))
                        .homepage(lib.path("homepage").asText(""))
                        .closed(lib.path("closed").asText(""))
                        .operatingTime(lib.path("operatingTime").asText(""))
                        .build());
                if (result.size() >= HOLDINGS_LIMIT) break;
            }
        } catch (Exception e) {
            log.error("소장 도서관 조회 오류 - isbn13:{}, regionCode:{}", isbn13, regionCode, e);
            return null;
        }

        log.info("{}.findLibrariesByBook End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    // null = 조회 실패(API 오류/타임아웃), 빈 리스트 = 정상 조회했지만 일치하는 도서관 없음
    @Override
    public List<LibraryDto> searchLibraries(String libName) {
        log.info("{}.searchLibraries Start! - libName:{}", this.getClass().getName(), libName);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("정보나루 API 인증키(library.api.key)가 설정되지 않아 도서관 검색을 건너뜁니다.");
            return List.of();
        }
        if (libName == null || libName.isBlank()) {
            return List.of();
        }

        List<LibraryDto> all = getAllLibraries();
        if (all == null) {
            log.warn("도서관 목록을 불러오지 못해 검색을 수행할 수 없습니다.");
            return null;
        }

        String keyword = libName.trim();
        List<LibraryDto> result = all.stream()
                .filter(lib -> lib.libName() != null && lib.libName().contains(keyword))
                .limit(LIBRARY_SEARCH_LIMIT)
                .toList();

        log.info("{}.searchLibraries End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    // 도서관 목록 조회 (캐시 우선). libSrch API가 도서관명 키워드 검색을 지원하지 않아
    // 목록을 받아온 뒤 searchLibraries()에서 이름으로 직접 필터링한다.
    // 정보나루 서버가 대량/연속 조회 시 매우 불안정해(504/502 잦음) 1회 조회로 제한하고 결과를 오래 캐싱한다.
    // 조회 자체가 실패하면 null을 반환해 "결과 없음"과 "조회 실패"를 구분한다
    private List<LibraryDto> getAllLibraries() {
        String cached = redisTemplate.opsForValue().get(LIBRARY_LIST_CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<LibraryDto>>() {});
            } catch (Exception e) {
                log.warn("도서관 목록 캐시 파싱 실패 - API 재조회: {}", e.getMessage());
            }
        }

        List<LibraryDto> page = fetchLibraryPage(1);
        if (page.isEmpty()) {
            return null; // 전국 도서관이 0건일 수는 없으므로 조회 실패로 간주
        }

        try {
            redisTemplate.opsForValue().set(LIBRARY_LIST_CACHE_KEY, objectMapper.writeValueAsString(page), LIBRARY_LIST_CACHE_TTL);
        } catch (Exception e) {
            log.warn("도서관 목록 캐시 저장 실패: {}", e.getMessage());
        }

        return page;
    }

    // libSrch API 1페이지 조회. 응답이 느리면 LIBRARY_API_TIMEOUT 이후 포기하고,
    // 실패해도 예외를 던지지 않고 빈 리스트를 반환한다 (호출부에서 null/빈 리스트로 성공/실패 구분)
    private List<LibraryDto> fetchLibraryPage(int pageNo) {
        List<LibraryDto> page = new ArrayList<>();
        try {
            String json = WebClient.create(baseUrl).get()
                    .uri(b -> b.path("/libSrch")
                            .queryParam("authKey", apiKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("pageSize", LIBRARY_PAGE_SIZE)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(LIBRARY_API_TIMEOUT)
                    .block();

            if (json == null || json.isBlank()) return page;

            JsonNode root = objectMapper.readTree(json);
            for (JsonNode entry : unwrapArray(root.path("response").path("libs"))) {
                JsonNode lib = unwrapSingle(entry, "lib");
                page.add(LibraryDto.builder()
                        .libCode(lib.path("libCode").asText(""))
                        .libName(lib.path("libName").asText(""))
                        .address(lib.path("address").asText(""))
                        .homepage(lib.path("homepage").asText(""))
                        .operatingTime(lib.path("operatingTime").asText(""))
                        .build());
            }
        } catch (Exception e) {
            log.error("도서관 목록 조회 오류 - pageNo:{}", pageNo, e);
        }
        return page;
    }

    // 정보나루 API는 목록성 JSON을 배열 또는 단일 객체로 줄 수 있어 항상 배열처럼 순회할 수 있도록 보정
    private Iterable<JsonNode> unwrapArray(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (node.isArray()) return node;
        return List.of(node);
    }

    // {"doc": {...}} 형태로 한 번 더 감싸진 경우와 그렇지 않은 경우를 모두 지원
    private JsonNode unwrapSingle(JsonNode node, String key) {
        JsonNode inner = node.path(key);
        return inner.isMissingNode() ? node : inner;
    }
}
