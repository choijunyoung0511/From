package com.from.service;

import com.from.dto.WeatherForecastDto;

import java.util.List;
//class가 아니라 interface로 작성한 이유는 메서드의 규칙을 정의하는 역할을 하기 위해서
public interface IWeatherService {

    //한번 호출 시 여러시간대의 예보 데이터가 반환됨, 각 시간대의 날씨를 DTO로 만들고 여러개의 DTO를 담기위해 List 사용
    // service에서 날씨 데이터를 WeatherForcastDTO여러 개를 결과로 반환
    List<WeatherForecastDto> getUltraShortForecast();
}
