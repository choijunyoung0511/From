package com.from.dto;

import lombok.Builder;

// 초단기예보(getUltraSrtFcst) 시간대별 예보 1건
// 기상청 원본 응답의 카테고리 코드(T1H, SKY, PTY, REH, RN1)를 사람이 읽을 수 있는 값으로 변환해 담는다
@Builder
public record WeatherForecastDto(
        //record는 데이터를 전달하기 위한 객체를 간결하게 정의하는 java문법, 한번 값을 넣으면 바꿀수 없음(값을 안전하게 운반하는 용도임)
        String fcstDate,          // 예보 날짜 (yyyyMMdd)
        String fcstTime,          // 예보 시각 (HHmm)
        String temperature,       // 기온 (T1H, ℃)
        String skyCondition,      // 하늘 상태: 맑음 / 구름많음 / 흐림
        String precipitationType, // 강수 형태: 없음 / 비 / 비/눈 / 눈 / 소나기
        String humidity,          // 습도 (REH, %)
        String precipitation      // 1시간 강수량 (RN1, 범주형 문자열, 예: "강수없음", "1.0mm")
) {}
