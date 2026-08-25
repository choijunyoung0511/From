package com.from.service;

import com.from.dto.FollowCountDto;
import com.from.dto.FollowToggleDto;

public interface IFollowService {

    // 팔로우 토글 — 팔로우 중이면 취소, 아니면 추가
    // 자기 자신 팔로우거나 대상 사용자가 존재하지 않으면 IllegalArgumentException 발생
    FollowToggleDto toggleFollow(String followerId, String followingId);

    // 특정 사용자의 팔로워/팔로잉 수 조회
    FollowCountDto getFollowCounts(String userId);

    // 로그인한 사용자가 대상 사용자를 팔로우 중인지 여부
    boolean isFollowing(String followerId, String followingId);
}
