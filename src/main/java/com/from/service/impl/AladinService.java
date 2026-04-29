package com.from.service.impl;

import com.from.dto.BookSearchDTO;
import com.from.service.IAladinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 알라딘 오픈 API 서비스 구현체.
 * 알라딘 API는 XML 형식으로 응답하므로 DOM 파서로 파싱하여 BookSearchDTO로 변환한다.
 */
@Slf4j
@Service
public class AladinService implements IAladinService {

    /** application.properties의 aladin.api.key 값 주입 */
    @Value("${aladin.api.key}")
    private String apiKey;

    /**
     * 알라딘 책 검색 API(ItemSearch)를 호출하여 검색 결과를 반환한다.
     * 검색 유형(Title/Author/Keyword)에 따라 QueryType 파라미터를 변환한다.
     *
     * @param query 검색어
     * @param type  검색 유형 (Title / Author / Keyword)
     * @return 검색 결과 (최대 10건)
     */
    @Override
    public List<BookSearchDTO> searchBooks(String query, String type) {
        log.info("{}.searchBooks Start! - query:{}, type:{}", this.getClass().getName(), query, type);
        List<BookSearchDTO> result = new ArrayList<>();
        try {
            // 프론트에서 받은 type을 알라딘 API 파라미터 값으로 변환
            String queryType = switch (type) {
                case "Title"  -> "Title";
                case "Author" -> "Author";
                default       -> "Keyword";
            };

            // 알라딘 ItemSearch API 호출 (WebClient: 비동기 HTTP 클라이언트)
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(b -> b.path("/ttb/api/ItemSearch.aspx")
                            .queryParam("TTBKey",      apiKey)
                            .queryParam("Query",       query)
                            .queryParam("QueryType",   queryType)
                            .queryParam("MaxResults",  10)
                            .queryParam("SearchTarget","Book")
                            .queryParam("output",      "xml")
                            .queryParam("Version",     "20131101")
                            .build())
                    .retrieve().bodyToMono(String.class).block();

            // XML 응답에서 <item> 태그를 파싱하여 BookSearchDTO로 변환
            NodeList items = parseXml(xml).getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                result.add(BookSearchDTO.builder()
                        .title(getTagValue("title",  item))
                        .author(getTagValue("author", item))
                        .cover(getTagValue("cover",  item))
                        .isbn(getTagValue("isbn13", item))
                        .build());
            }
        } catch (Exception e) {
            log.error("알라딘 검색 오류", e);
        }
        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    /**
     * 알라딘 베스트셀러 API(ItemList)를 호출하여 Top 10을 반환한다.
     *
     * @return 베스트셀러 목록 (최대 10건)
     */
    @Override
    public List<BookSearchDTO> getBestseller() {
        log.info("{}.getBestseller Start!", this.getClass().getName());
        List<BookSearchDTO> result = new ArrayList<>();
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(b -> b.path("/ttb/api/ItemList.aspx")
                            .queryParam("TTBKey",      apiKey)
                            .queryParam("QueryType",   "Bestseller")
                            .queryParam("MaxResults",  10)
                            .queryParam("SearchTarget","Book")
                            .queryParam("output",      "xml")
                            .queryParam("Version",     "20131101")
                            .build())
                    .retrieve().bodyToMono(String.class).block();

            NodeList items = parseXml(xml).getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                result.add(BookSearchDTO.builder()
                        .title(getTagValue("title",  item))
                        .author(getTagValue("author", item))
                        .cover(getTagValue("cover",  item))
                        .isbn(getTagValue("isbn13", item))
                        .build());
            }
        } catch (Exception e) {
            log.error("베스트셀러 API 오류", e);
        }
        log.info("{}.getBestseller End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    /**
     * 책 제목으로 표지 이미지 URL을 1건 조회한다.
     * AI 추천 결과에 표지를 붙일 때 사용하며, 없으면 빈 문자열을 반환한다.
     */
    @Override
    public String searchCover(String title) {
        try {
            String xml = WebClient.create("https://www.aladin.co.kr").get()
                    .uri(b -> b.path("/ttb/api/ItemSearch.aspx")
                            .queryParam("TTBKey",      apiKey)
                            .queryParam("Query",       title)
                            .queryParam("QueryType",   "Title")
                            .queryParam("MaxResults",  1)
                            .queryParam("SearchTarget","Book")
                            .queryParam("output",      "xml")
                            .queryParam("Version",     "20131101")
                            .build())
                    .retrieve().bodyToMono(String.class).block();

            NodeList items = parseXml(xml).getElementsByTagName("item");
            if (items.getLength() > 0) return getTagValue("cover", (Element) items.item(0));
        } catch (Exception e) {
            log.error("알라딘 표지 검색 오류: {}", title, e);
        }
        return "";
    }

    /**
     * XML 문자열을 DOM Document로 파싱한다.
     */
    private Document parseXml(String xml) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * XML Element에서 특정 태그의 텍스트 값을 추출한다.
     * 태그가 없거나 텍스트가 없으면 빈 문자열을 반환한다.
     *
     * @param tag     추출할 태그명 (예: "title", "author", "cover")
     * @param element 검색 대상 XML 엘리먼트
     * @return 태그 텍스트 값 또는 빈 문자열
     */
    private String getTagValue(String tag, Element element) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() > 0 && list.item(0).getChildNodes().getLength() > 0) {
            return list.item(0).getChildNodes().item(0).getNodeValue();
        }
        return "";
    }
}