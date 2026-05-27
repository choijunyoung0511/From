package com.from.service.impl;

// JSON 파싱을 위한 Jackson 라이브러리
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.from.dto.BookSearchDto;
import com.from.service.IAladinService;

import lombok.extern.slf4j.Slf4j;

// Spring Bean 등록
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 외부 API 호출용 HTTP 클라이언트
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

//책검색,베스트셀러,표지이미지 조회
@Slf4j
@Service
public class AladinService implements IAladinService {

    @Value("${aladin.api.key}")
    private String apiKey;

   //jackson
    private final ObjectMapper objectMapper = new ObjectMapper();


     // 알라딘 책 검색 API 호출
     //Controller → AladinService.searchBooks()
     //알라딘 API 호출
     //JSON 결과 수신
     //DTO로 변환
    private String cleanQuery(String query) {
        if (query == null) return "";
        if (query.contains("-")) query = query.split("-")[0].trim();
        query = query.replaceAll("[?！!·…]", "").trim();
        return query;
    }

    @Override
    public List<BookSearchDto> searchBooks(String query, String type) {
        String cleanedQuery = cleanQuery(query);
        log.info("{}.searchBooks Start! - query:{}, type:{}", this.getClass().getName(), cleanedQuery, type);

       //제목,저자,키워드
        String queryType = switch (type) {
            case "Title"  -> "Title";
            case "Author" -> "Author";
            default       -> "Keyword";
        };

        List<BookSearchDto> result = new ArrayList<>();

        try {

           //웹 클라이언트로 api 호출
            String json = WebClient.create("https://www.aladin.co.kr").get()

                    // API 경로
                    .uri(b -> b.path("/ttb/api/ItemSearch.aspx")

                            // API 인증 키
                            .queryParam("TTBKey", apiKey)

                            // 검색어
                            .queryParam("Query", cleanedQuery)

                            // 검색 타입
                            .queryParam("QueryType", queryType)

                            // 최대 결과 수
                            .queryParam("MaxResults", 100)

                            // 검색 대상
                            .queryParam("SearchTarget", "Book")

                            // JSON 응답
                            .queryParam("output", "JS")

                            // API 버전
                            .queryParam("Version", "20131101")

                            .build())

                    // API 요청 실행
                    .retrieve()

                    // 응답을 String(JSON)으로 받음
                    .bodyToMono(String.class)

                    // 비동기 → 동기 처리
                    .block();

          //json -> dto
            result = parseItems(json);

        } catch (Exception e) {
            log.error("알라딘 검색 오류 - query:{}, type:{}, error:{}", cleanedQuery, queryType, e.getMessage());
        }

        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());

        return result;
    }

    //베스트 셀러
    @Override
    public List<BookSearchDto> getBestseller() {

        log.info("{}.getBestseller Start!", this.getClass().getName());

        List<BookSearchDto> result = new ArrayList<>();

        try {

           //알라딘 api 호출
            String json = WebClient.create("https://www.aladin.co.kr").get()

                    .uri(b -> b.path("/ttb/api/ItemList.aspx")

                            .queryParam("TTBKey", apiKey)

                            // 베스트셀러 조회
                            .queryParam("QueryType", "Bestseller")

                            .queryParam("MaxResults", 100)

                            .queryParam("SearchTarget", "Book")

                            .queryParam("output", "JS")

                            .queryParam("Version", "20131101")

                            .build())

                    .retrieve()

                    .bodyToMono(String.class)

                    .block();

            //json -> dto
            result = parseItems(json);

        } catch (Exception e) {

            log.error("베스트셀러 API 오류", e);
        }

        log.info("{}.getBestseller End! - {}건", this.getClass().getName(), result.size());

        return result;
    }

    //제목으로 표지 이미지 조회
    @Override
    public String searchCover(String title) {
        String cleanedTitle = cleanQuery(title);
        try {

            String json = WebClient.create("https://www.aladin.co.kr").get()

                    .uri(b -> b.path("/ttb/api/ItemSearch.aspx")

                            .queryParam("TTBKey", apiKey)

                            // 제목 검색
                            .queryParam("Query", cleanedTitle)

                            .queryParam("QueryType", "Title")

                            // 결과 1개만
                            .queryParam("MaxResults", 1)

                            .queryParam("SearchTarget", "Book")

                            .queryParam("output", "JS")

                            .queryParam("Version", "20131101")

                            .build())

                    .retrieve()

                    .bodyToMono(String.class)

                    .block();

            //json -> dto반환
            List<BookSearchDto> items = parseItems(json);

            //첫번째 표지
            if (!items.isEmpty())
                return items.get(0).cover();

        } catch (Exception e) {

            log.error("알라딘 표지 검색 오류: {}", title, e);
        }

        return "";
    }

    //JSON 데이터를 BookSearchDto 리스트로 변환하는 메서드
    // {
    //            "title":"책 제목",
    //            "author":"저자",
    //            "cover":"이미지 URL",
    //            "isbn13":"ISBN국제번호"
    //         }
    private List<BookSearchDto> parseItems(String json) throws Exception {

        List<BookSearchDto> result = new ArrayList<>();

        // JSON 문자열 → JSON 트리 구조 변환
        JsonNode root = objectMapper.readTree(json);

        // item 배열 가져오기
        JsonNode items = root.path("item");

        // 각 책 데이터 반복
        for (JsonNode item : items) {

            // DTO 생성
            result.add(BookSearchDto.builder()

                    .title(item.path("title").asText(""))

                    .author(item.path("author").asText(""))

                    .cover(item.path("cover").asText(""))

                    .isbn(item.path("isbn13").asText(""))

                    .description(item.path("description").asText(""))

                    .category(item.path("categoryName").asText(""))

                    .build());
        }

        return result;
    }
}