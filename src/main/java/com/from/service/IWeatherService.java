package com.from.service;

import com.from.dto.WeatherForecastDto;

import java.util.List;

public interface IWeatherService {

    // 초단기예보 조회 (현재 시각 기준 향후 약 6시간, 1시간 단위)
    List<WeatherForecastDto> getUltraShortForecast();
}
