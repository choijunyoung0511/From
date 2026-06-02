package com.from.dto;

import lombok.Builder;

@Builder
public record UserInfoDto(
        //회원가입
        String userId,
        String username,
        String password,
        String name,
        String email,
        String profileImageUrl,
        String existsYn
) {}