package com.from.dto;

import java.time.LocalDateTime;

//게시글 목록/상세 화면에 전달하는 DTO
public record BoardDto(
        Long id,
        String userId,   //작성자 로그인 아이디 (본인 여부 판단용)
        String writer,    //작성자 닉네임 (화면 표시용)
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
