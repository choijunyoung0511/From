package com.from.dto;

public record LikeToggleDto(
        boolean liked, //좋아요 눌림  & 안눌림
        long count) {}