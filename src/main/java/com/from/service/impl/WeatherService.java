package com.from.service.impl;

import com.fasterxml.jackson.databind.JsonNode; //api에서 받은 Json구조에서 내부 값을 탐색하고 읽기 위해 사용
import com.fasterxml.jackson.databind.ObjectMapper;//json문자열을 JsonNode 등 java에서 처리 가능한 객체로 변환하기 위해 사용
import com.from.dto.WeatherForecastDto;
import com.from.service.IWeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;//설정 파일에 있는 값을 java코드로 가져올떄 사용
import org.springframework.stereotype.Service;//여기가 서비스 계층
import org.springframework.web.reactive.function.client.WebClient; //외부api에 http요청을 보내고 응답을 받아올때 사용하는 도구
//webclient에서 기상청api 호출하고 json 문자열 받음, ObjectMapper로 변환 후, JsonNode로 내부 데이터 검색

import java.time.LocalDateTime; //기상청 api가 요청할 기준 날짜와 기준 시간을 계산해야하기 떄문에 필요
import java.time.format.DateTimeFormatter; //계산한 날짜를 어떤 모양으로 보여줄지 정함
import java.util.ArrayList; //List를 실제로 사용할  수있게 구현한 클래스
import java.util.LinkedHashMap; //데이터를 key,value형태로 저장(Map을 실제로 구현한 클래스)
import java.util.List;
import java.util.Map;
import java.util.TreeMap;//key를 기준으로 정렬된 상태로 데이터를 관리하기 위해 사용하느 map구현체임

// 기상청 API 허브 - 동네예보(초단기예보, getUltraSrtFcst) 조회
// AladinService와 동일한 패턴: WebClient로 호출 → Jackson으로 JSON 트리 파싱 → DTO 변환
@Slf4j
@Service //WeatherService를 Service계층의 Spring Bean으로 등록해서 Spring이 객체 생성 후 관리
public class WeatherService implements IWeatherService {
    //WeatherService가 IweatherService에서 정의한 기능을 실제로 구현하는 클래스라는걸 암시


    //Value로 스프링 설정에 있는 값 찾음, 아래 변수에 저장
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


    //Jackson라이브러리의 ObjectMapper 클래스에 정의되어 있는 생성자를 호출해서 새로운 ObjectMapper객체를 생성하고, 그 객체를 objectMapper 변수에 저장
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    //인터페이스 에서 선언한 getUltraShortForecast를 현재 클래스에서 재정의해서 구현
    //매개변수없이 초단기예보를 조회하고 조회결과를 여러개의 WeatherForecastDto가 담긴 List 형태
    public List<WeatherForecastDto> getUltraShortForecast() {
        //다른클래스 에서도 사용할수 있게 public(WeatherController에서 사용)
        log.info("{}.getUltraShortForecast Start!", this.getClass().getName());
        //최종 날씨 결과들을 담아둘 빈 List객체를 만드는 코드임
        List<WeatherForecastDto> result = new ArrayList<>();

        try {
            String[] baseDateTime = calculateBaseDateTime(); //기상청api 에 전달한 기준날짜와 기준시간을 계산해서 받아오는것
            String baseDate = baseDateTime[0]; //기준 날짜 저장
            String baseTime = baseDateTime[1]; //기준 시간 저장

            //요청을 보낼 기본 주소를 baseUrl로 설정함
            String json = WebClient.create(baseUrl).get()
                    //기상청 api에 get방식으로 요청함
                    .uri(b -> b.path("/getUltraSrtFcst")
                            // 기상청 API 허브는 인증 파라미터로 authKey를 사용,이건 기상청 api에서 실제로 어떤 기능을 호출할지 URL경로를 붙이는 부분임
                            //기본 baseUrl에다가 + getUltraStrFcst 기상청 초 단기예보 api조회
                            .queryParam("authKey", apiKey) //설정에서 가져온 기상청api 인증키를 authKey라는 요청 파라미터로 전달함
                            .queryParam("pageNo", 1) //기상청 api조회 결과 중 첫 번쨰 페이지를 요청한다
                            .queryParam("numOfRows", 1000) //한페이지에 최대 몇개의 데이털르 받아올지 지정하는 파라미터
                            .queryParam("dataType", "JSON") //기상청 api의 응답형식을 json으로 지정한다
                            .queryParam("base_date", baseDate)//날짜 기준
                            .queryParam("base_time", baseTime)//시간 기준
                            .queryParam("nx", nx) //x좌표
                            .queryParam("ny", ny) //y 좌표
                            .build()) //하나씩 조립한걸 가지고 지금까지의 설정한  값들로 실제 요청할 URL를 완성함
                    .retrieve() //설정한 get 요청을 수행하고, 서버에 돌아오는 HTTP 응답을 처리하는 단계로 넘어감
                    .bodyToMono(String.class) //기상청 서버의 응답 본문을 String형태로 반환,비동기 방식임(응답 본문을 String으로 받을 준비를 한다)
                    .block(); //api 응답이 올떄 까지 기다린 다음 실제 응답 값을 꺼내서 Json변수에 저장할수 있게함

            result = parseForecast(json); //json문자열을 parseForecast()에 전달해 분석하고, 변환된 날씨 DTO목록을 result에 저장

        } catch (Exception e) {
            log.error("날씨 API 오류", e);
        }

        log.info("{}.getUltraShortForecast End! - {}건", this.getClass().getName(), result.size());
        return result;
    }

    // 초단기예보 발표시각 규칙: 매시 30분에 생성, 실제 제공은 45분 이후
    // → 현재 시각 기준 45분을 빼서 아직 발표 전인 시각을 참조하지 않도록 함
    private String[] calculateBaseDateTime() {
        LocalDateTime base = LocalDateTime.now().minusMinutes(45); //기상청 api에 사용할 기준 시간을 계산 ,현재시간에서 - 45분을 뺌
        // 그 값이 LocalDateTime base에 저장,
        // 45분을 뺸 이유는 아직 제공되지 않을 수 있는 최신 시점을 피하기 위해서
        String baseDate = base.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = base.format(DateTimeFormatter.ofPattern("HH")) + "30";
        return new String[]{baseDate, baseTime}; //위에서 호출한 배열에  값을 보내줌
    }

    // 기상청 응답은 (시각, 카테고리) 조합이 각각 별도 item으로 오기 때문에
    // fcstTime을 기준으로 묶어서 하나의 시간대별 DTO로 재구성한다
    private List<WeatherForecastDto> parseForecast(String json) throws Exception {
        //매서드 이름이 paeseForecast임 받은 데이터를 분석해서 필요한 형태로 변환
        List<WeatherForecastDto> result = new ArrayList<>();


        //json 내용이 들어있는 문자열을 읽어서 json트리 구조로 변환한다
        JsonNode root = objectMapper.readTree(json); //String을 json구조로 변환하는 도구
        JsonNode items = root.path("response").path("body").path("items").path("item");//json 구조를 담고 탐색하는 객체
        //최종적인 실제 예보 항목들은 item에 들어가있음
        // fcstTime → (category → value) 로 그룹핑. TreeMap으로 시간 순 정렬

        //시간대별로 날씨 데이얼르 묶어서 저장할 공간을 만드는 코드임
        //맨 바깥쪽 map은 시간 안쪽map은 날씨 항목
        // 시간 key를 정렬된 순서로 관리하기 위해서 TreeMap사용
        Map<String, Map<String, String>> grouped = new TreeMap<>();
        String fcstDate = "";


        //날씨데이터를 하나씩 꺼내서 반복처리하는 for-each문
        for (JsonNode item : items) {
            String time = item.path("fcstTime").asText(""); //예보시간을 가져오는 코드
            fcstDate = item.path("fcstDate").asText(fcstDate); //
            String category = item.path("category").asText(""); //날씨데이터의 종류를 가져오는 코드
            String value = item.path("fcstValue").asText(""); //실제 예보값을 가져오는 코드

            //시간/카테고리/값을 실제 groupend에 저장하는 부분임
            //gropend에 fcstTIme이라는 시간이 없으면 그 시간에 해당하는 새로운 LinkedHashMap을 만들어라(computeIfAbsent가 그 역할을 해쥼)
            grouped.computeIfAbsent(time, k -> new LinkedHashMap<>()).put(category, value);
        }
        //위에서만든 groupend를 시간대별로 하나씩 꺼내는 반복문
        // 시간(key) + 그 시간의 날씨 정보를 한세트로 담는 변수
        for (Map.Entry<String, Map<String, String>> entry : grouped.entrySet()) {
            //현재 시간대 entry에서 날씨 정보 Map을 꺼내 values에 저장
            Map<String, String> values = entry.getValue();

            result.add(WeatherForecastDto.builder()
                    //Builder를 이용해 DTO 객체를 생성 시작하고, 완성된 DTO를 result List에 추가하기 위한 코드임
                    .fcstDate(fcstDate) //예보날짜
                    .fcstTime(entry.getKey()) //예보 시간
                    .temperature(values.getOrDefault("T1H", ""))//기온
                    .skyCondition(toSkyText(values.get("SKY"))) //하늘 상태
                    .precipitationType(toPtyText(values.get("PTY"))) //강수 상태
                    .humidity(values.getOrDefault("REH", "")) //습도
                    .precipitation(values.getOrDefault("RN1", ""))  //1시간 강수량
                    .build());
        }

        return result;
    }

    // SKY 코드: 1=맑음, 3=구름많음, 4=흐림(하늘상태)
    private String toSkyText(String code) {
        // 기상청의 sky코드를 문자열로 받아 사람이 이해할수 있는 하늘 상태 문자열로 변환해서 반환하는 메서드
        if (code == null) return "";
        return switch (code) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> code; //정해놓은 sky코드가 아니면 들어온 코드값 자체를 그대로 반환한다.
        };
    }

    // PTY 코드: 0=없음, 1=비, 2=비/눈, 3=눈, 4=소나기
    //사람이 이해할수 있는 강수 형태 문자열로 반환하는 메서드
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
