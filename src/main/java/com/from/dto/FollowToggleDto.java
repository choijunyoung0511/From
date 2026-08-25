package com.from.dto;

public record FollowToggleDto(
        //팔오우 언팔로우 처리 결과를 전달하기 위한 DTO임
        boolean following, //팔로우 중 & 아님
        //팔로우 취소 처리후에 상대방의 팔로워수
        long followerCount) {}
