package com.from.dto.user;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SessionUser {
    private Long userId;
    private String username;
    private String name;
    private String email;
}
