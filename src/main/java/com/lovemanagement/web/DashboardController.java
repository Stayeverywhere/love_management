package com.lovemanagement.web;

import com.lovemanagement.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/pm/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("board", dashboardService.snapshot());
        return "pm/dashboard";
    }

    @GetMapping("/pm/trends")
    public String trends(Model model,
                         @RequestParam(value = "granularity", required = false, defaultValue = "day") String granularity) {
        DashboardService.TrendSeries series = dashboardService.trend(granularity);
        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < series.labels().size(); i++) {
            points.add(new TrendPoint(series.labels().get(i), series.values().get(i)));
        }
        model.addAttribute("trend", series);
        model.addAttribute("points", points);
        model.addAttribute("granularity", granularity);
        String unitLabel = switch (granularity) {
            case "week" -> "近 8 周（周一为一周起点）";
            case "month" -> "近 6 个月";
            default -> "近 7 日（包含今天）";
        };
        model.addAttribute("unitLabel", unitLabel);
        model.addAttribute("dimensionLabel", switch (granularity) {
            case "week" -> "周";
            case "month" -> "月份";
            default -> "日期";
        });
        return "pm/trend";
    }

    public record TrendPoint(String label, long value) {
    }
}
