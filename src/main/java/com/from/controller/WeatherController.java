// WeatherController.java - 날씨 조회 API
// 담당 기능: 초단기예보(향후 약 6시간) 조회
package com.from.controller;

import com.from.dto.WeatherForecastDto;
import com.from.service.IWeatherService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final IWeatherService weatherService;

    // [POST] /weather/forecast - 초단기예보(향후 약 6시간, 1시간 단위) 조회
    @PostMapping("/forecast")
    public List<WeatherForecastDto> forecast() {
        log.info("{}.forecast Start!", this.getClass().getName());

        List<WeatherForecastDto> result = weatherService.getUltraShortForecast();

        log.info("{}.forecast End! - {}건", this.getClass().getName(), result.size());
        return result;
    }
}
