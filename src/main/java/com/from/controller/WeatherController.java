// WeatherController.java - 날씨 조회 API
// 담당 기능: 초단기예보(향후 약 6시간) 조회
package com.from.controller;

import com.from.dto.WeatherForecastDto;
import com.from.service.IWeatherService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j //로그
@RestController //스프링이 객체를 자동으로 생성해줌 -> Http요청 받을수 있게됨
@RequestMapping("/weather")// WeatherController에서 사용하는 api들의 공통url 앞부분은 weather로 지정
@RequiredArgsConstructor //필요한 필드를 매개변수로 받는 생성자를 자동을 만들어주는 것,생성자는 객체를 새로 생성될떄마다 한번 실행됨
public class WeatherController {
    //servie 패키지에 있는 IWeatherService를 현재 COntroller에서 사용하기 위해 가져옴
    // 즉 controller에서 날씨 관련 Service를 사용하기 위해서 import함
    //Servie가 이 클래스 내부에서만 접근 가능하다고 제안
    //IWeatherService타입의 weatherService를 선언하고 외부접근 제한
    //위에서 RequiredArgsConstructor를 선언함 final로 선언된 필드의 생성자를 자동으로 만들어줌

    private final IWeatherService weatherService;


    // [POST] /weather/forecast - 초단기예보(향후 약 6시간, 1시간 단위) 조회
    // forecast 요청받고 서비스 호출하는구조
    @PostMapping("/forecast")
    //날씨 조회 결과를 DTO에다가 담아서 반환하기위해 import
    //같은 타입의 데이터를 여러개 담기 위해서 리스트 사용
    //forcast()는 별도의 매개변수 없이 실행되고 실행 결과로 여러개의 WeatherForecastDto가 담긴 List 반환하는 메서드
    public List<WeatherForecastDto> forecast() {
        log.info("{}.forecast Start!", this.getClass().getName());
    //컨트롤러 에서 서비스로 넘어가는 부분이 이곳임
    //서비스에서 받아온 날씨조회 결과를 저장할 변수가 result임
        List<WeatherForecastDto> result = weatherService.getUltraShortForecast();

        log.info("{}.forecast End! - {}건", this.getClass().getName(), result.size());
        return result;
    }
}
