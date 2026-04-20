package com.from.service.impl;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AladinService implements IAladinService {

    @Value("${aladin.api.key}")
    private String apiKey;

    @Override
    public List<Map<String, String>> searchBooks(String query, String type) {
        log.info("{}.searchBooks Start! - query:{}, type:{}", this.getClass().getName(), query, type);
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String queryType = switch (type) {
                case "Title"  -> "Title";
                case "Author" -> "Author";
                default       -> "Keyword";
            };
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

            NodeList items = parseXml(xml).getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                Map<String, String> book = new HashMap<>();
                book.put("title",  getTagValue("title",  item));
                book.put("author", getTagValue("author", item));
                book.put("cover",  getTagValue("cover",  item));
                book.put("isbn",   getTagValue("isbn13", item));
                result.add(book);
            }
        } catch (Exception e) {
            log.error("알라딘 검색 오류", e);
        }
        log.info("{}.searchBooks End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    @Override
    public List<Map<String, String>> getBestseller() {
        log.info("{}.getBestseller Start!", this.getClass().getName());
        List<Map<String, String>> result = new ArrayList<>();
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
                Map<String, String> book = new HashMap<>();
                book.put("title",  getTagValue("title",  item));
                book.put("author", getTagValue("author", item));
                book.put("cover",  getTagValue("cover",  item));
                book.put("isbn",   getTagValue("isbn13", item));
                result.add(book);
            }
        } catch (Exception e) {
            log.error("베스트셀러 API 오류", e);
        }
        log.info("{}.getBestseller End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

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

    private Document parseXml(String xml) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String getTagValue(String tag, Element element) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() > 0 && list.item(0).getChildNodes().getLength() > 0) {
            return list.item(0).getChildNodes().item(0).getNodeValue();
        }
        return "";
    }
}