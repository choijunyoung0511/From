package com.from.dto;

public record FollowCountDto(
        long followerCount,  //나를 팔로우하는 사람 수
        long followingCount) {} //내가 팔로우하는 사람 수
