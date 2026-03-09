package com.from.domain;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalTime;

@Getter
@Setter
@ToString


public class User {
    private Long userId;
    private String username;
    private String password;
    private String name;
    private String email;
    private LocalTime createAt;
    private LocalTime updatedAt;
    private LocalTime deleteAt;
    private Long kakaoId;
    private String loginType;
}
