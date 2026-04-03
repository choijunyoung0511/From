package com.from.dto;

import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record MsgDTO(
        int result,
        String msg
) {}