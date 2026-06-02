package com.from.dto;

import lombok.Builder;

@Builder
public record MsgDto(
        //결과메시지 전달용,컨트롤러 -> 프론트로 결과 상태와 메시지를 전달하는 공통 DTO
        int result, //성공/실패 코드
        String msg, //사용자에게 보여줄 메시지
        Long bookId //필요시 추가 데이터
) {}