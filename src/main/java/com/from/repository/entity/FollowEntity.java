package com.from.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

//팔로우 관계 테이블과 매핑되는 엔터티 (followerId 가 followingId 를 팔로우함)
@Entity
@Table(name = "follows")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class FollowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //나를 팔로우 하는 사람 수
    @Column(nullable = false, length = 20)
    private String followerId;

    //내가 팔로우 하는 사람 수
    @Column(nullable = false, length = 20)
    private String followingId;
}
