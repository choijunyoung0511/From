package com.from.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.from.dto.WeatherForecastDto;
import com.from.service.IWeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 기상청 API 허브 - 동네예보(초단기예보, getUltraSrtFcst) 조회
// AladinService와 동일한 패턴: WebClient로 호출 → Jackson으로 JSON 트리 파싱 → DTO 변환
@Slf4j
@Service
public class WeatherService implements IWeatherService {

    @Value("${weather.api.key}")
    private String apiKey;

    // 기상청 API 허브 기본 주소 (문서상 host/path가 다르면 application.properties에서 덮어쓰면 됨)
    @Value("${weather.api.base-url:https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0}")
    private String baseUrl;

    // 격자좌표: 위도/경도가 아닌 기상청 전용 좌표계. 기본값 서울(60,127) 임의값
    @Value("${weather.nx:60}")
    private int nx;

    @Value("${weather.ny:127}")
    private int ny;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<WeatherForecastDto> getUltraShortForecast() {
        log.info("{}.getUltraShortForecast Start!", this.getClass().getName());

        List<WeatherForecastDto> result = new ArrayList<>();

        try {
            String[] baseDateTime = calculateBaseDateTime();
            String baseDate = baseDateTime[0];
            String baseTime = baseDateTime[1];

            String json = WebClient.create(baseUrl).get()
                    .uri(b -> b.path("/getUltraSrtFcst")
                            // 기상청 API 허브는 인증 파라미터로 authKey를 사용
                            .queryParam("authKey", apiKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 1000)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", baseDate)
                            .queryParam("base_time", baseTime)
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            result = parseForecast(json);

        } catch (Exception e) {
            log.error("날씨 API 오류", e);
        }

        log.info("{}.getUltraShortForecast End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    // 초단기예보 발표시각 규칙: 매시 30분에 생성, 실제 제공은 45분 이후
    // → 현재 시각 기준 45분을 빼서 아직 발표 전인 시각을 참조하지 않도록 함
    private String[] calculateBaseDateTime() {
        LocalDateTime base = LocalDateTime.now().minusMinutes(45);
        String baseDate = base.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = base.format(DateTimeFormatter.ofPattern("HH")) + "30";
        return new String[]{baseDate, baseTime};
    }

    // 기상청 응답은 (시각, 카테고리) 조합이 각각 별도 item으로 오기 때문에
    // fcstTime을 기준으로 묶어서 하나의 시간대별 DTO로 재구성한다
    private List<WeatherForecastDto> parseForecast(String json) throws Exception {
        List<WeatherForecastDto> result = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.path("response").path("body").path("items").path("item");

        // fcstTime → (category → value) 로 그룹핑. TreeMap으로 시간 순 정렬
        Map<String, Map<String, String>> grouped = new TreeMap<>();
        String fcstDate = "";

        for (JsonNode item : items) {
            String time = item.path("fcstTime").asText("");
            fcstDate = item.path("fcstDate").asText(fcstDate);
            String category = item.path("category").asText("");
            String value = item.path("fcstValue").asText("");

            grouped.computeIfAbsent(time, k -> new LinkedHashMap<>()).put(category, value);
        }

        for (Map.Entry<String, Map<String, String>> entry : grouped.entrySet()) {
            Map<String, String> values = entry.getValue();

            result.add(WeatherForecastDto.builder()
                    .fcstDate(fcstDate)
                    .fcstTime(entry.getKey())
                    .temperature(values.getOrDefault("T1H", ""))
                    .skyCondition(toSkyText(values.get("SKY")))
                    .precipitationType(toPtyText(values.get("PTY")))
                    .humidity(values.getOrDefault("REH", ""))
                    .precipitation(values.getOrDefault("RN1", ""))
                    .build());
        }

        return result;
    }

    // SKY 코드: 1=맑음, 3=구름많음, 4=흐림
    private String toSkyText(String code) {
        if (code == null) return "";
        return switch (code) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> code;
        };
    }

    // PTY 코드: 0=없음, 1=비, 2=비/눈, 3=눈, 4=소나기
    private String toPtyText(String code) {
        if (code == null) return "";
        return switch (code) {
            case "0" -> "없음";
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            default -> code;
        };
    }
}
