package com.from.dto.user;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SignupRequestDto {
    private String username;
    private String password;
    private String passwordConfirm;
    private String name;
    private String email;
    private String emailCode;
    private String existYn;
}
