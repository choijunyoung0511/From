package com.from.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MainController {

    @GetMapping("/")
    public String index(HttpSession session, Model model) {

        if (session.getAttribute("loginRequired") != null) {
            model.addAttribute("loginRequired", true);
            session.removeAttribute("loginRequired");
        }

        return "main/index";
    }
}