package com.from.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/review")
public class ReviewController {

    // AI 독후감 생성 (기존)
    @GetMapping("/create")
    public String create() {
        return "review/create";
    }

    // AI 독후감 결과 (기존)
    @GetMapping("/result")
    public String result() {
        return "review/result";
    }

    // 책 속 주인공 이미지 생성 (신규)
    @GetMapping("/image")
    public String image() {
        return "review/image";
    }

    // 책 추천 (신규)
    @GetMapping("/recommend")
    public String recommend() {
        return "review/recommend";
    }
}
