package com.from.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RankingController {

    @GetMapping("/ranking")
    public String ranking(HttpSession session) {
        if (session.getAttribute("loginUser") == null)
            return "redirect:/user/login";
        return "ranking/ranking";
    }
}