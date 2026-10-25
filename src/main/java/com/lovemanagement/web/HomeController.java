package com.lovemanagement.web;

import com.lovemanagement.domain.AccountRole;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.service.CurrentUserService;
import com.lovemanagement.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CurrentUserService currentUserService;
    private final DashboardService dashboardService;

    public HomeController(CurrentUserService currentUserService, DashboardService dashboardService) {
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
    }

    /**
     * 首页 = 数据看板（项目经理/管理员）；团队成员首页为任务总览。
     */
    @GetMapping("/home")
    public String home(Model model) {
        UserAccount user = currentUserService.currentOrNull();
        if (user != null && user.getAccountRole() != AccountRole.MEMBER) {
            model.addAttribute("board", dashboardService.snapshot());
            return "pm/dashboard";
        }
        return "home";
    }
}
