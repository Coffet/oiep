package com.oiep.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Static "why OIEP exists" page. */
@Controller
public class AboutController {

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("currentUrl", "/about");
        model.addAttribute("activeNav", "about");
        return "about";
    }
}
