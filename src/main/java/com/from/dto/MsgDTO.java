package com.from.dto;

import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 AJAX 요청에 대한 서버 응답을 통일하는 DTO.
 * 컨트롤러에서 JSON으로 직렬화되어 브라우저로 전달된다.
 *
 * result: 1 = 성공, 0 = 실패
 * msg: 사용자에게 보여줄 메시지
 *
 * @JsonInclude(NON_DEFAULT): result=0(int 기본값)일 때는 JSON에서 result 필드를 생략하지 않음.
 * 단, String 타입인 msg가 null이면 생략됨.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record MsgDTO(
        int result,   // 1 = 성공, 0 = 실패
        String msg    // 처리 결과 메시지 (예: "등록되었습니다.")
) {}