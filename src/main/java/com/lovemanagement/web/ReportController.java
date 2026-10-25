package com.lovemanagement.web;

import com.lovemanagement.service.TaskReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ReportController {

    private final TaskReportService reportService;

    public ReportController(TaskReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/pm/reports")
    public String reports(Model model,
                          @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                          @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        TaskReportService.ReportResult result = reportService.distribution();
        List<TaskReportService.ReportRow> allRows = result.rows();
        int pageSize = Math.min(Math.max(size, 1), 50);
        int totalPages = (int) Math.ceil(allRows.size() / (double) pageSize);
        int current = Math.max(1, Math.min(page, Math.max(totalPages, 1)));
        int start = (current - 1) * pageSize;
        int end = Math.min(start + pageSize, allRows.size());
        List<TaskReportService.ReportRow> pageRows = start < allRows.size()
                ? allRows.subList(start, end)
                : List.of();

        model.addAttribute("rows", pageRows);
        model.addAttribute("memberCount", allRows.size());
        model.addAttribute("unassigned", result.unassigned());
        model.addAttribute("unassignedPercent", result.unassignedPercent());
        model.addAttribute("totalAll", result.totalAll());
        model.addAttribute("page", current);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalPages", Math.max(totalPages, 1));
        model.addAttribute("totalElements", allRows.size());
        model.addAttribute("hasPrevious", current > 1);
        model.addAttribute("hasNext", current < totalPages);
        model.addAttribute("prevUrl", "/pm/reports?page=" + (current - 1) + "&size=" + pageSize);
        model.addAttribute("nextUrl", "/pm/reports?page=" + (current + 1) + "&size=" + pageSize);
        model.addAttribute("pageUrlPrefix", "/pm/reports?size=" + pageSize);
        return "pm/report";
    }
}
