package com.lovemanagement.web;

import com.lovemanagement.service.CurrentUserService;
import com.lovemanagement.service.PersonalStatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PersonalStatsController {

    private final CurrentUserService currentUserService;
    private final PersonalStatsService statsService;

    public PersonalStatsController(CurrentUserService currentUserService, PersonalStatsService statsService) {
        this.currentUserService = currentUserService;
        this.statsService = statsService;
    }

    @GetMapping("/stats/personal")
    public String personal(Model model) {
        var account = currentUserService.requireCurrent();
        model.addAttribute("account", account);
        model.addAttribute("stats", statsService.statsOf(account));
        return "stats/personal";
    }
}
