package com.from.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    @GetMapping("/service")
    public String service(HttpSession session) {
        if (session.getAttribute("loginUser") == null) {
            return "redirect:/user/login";
        }
        return "service";
    }
}