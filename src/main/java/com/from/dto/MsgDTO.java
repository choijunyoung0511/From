package com.from.dto;

import lombok.Builder;

@Builder
public record MsgDto(
        int result,
        String msg,
        Long bookId
) {}