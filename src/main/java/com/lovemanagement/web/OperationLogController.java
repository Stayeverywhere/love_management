package com.lovemanagement.web;

import com.lovemanagement.service.OperationLogService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 操作日志（全局）。 */
@Controller
public class OperationLogController {

    private final OperationLogService logService;

    public OperationLogController(OperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/admin/logs")
    public String logs(Model model,
                       @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        Page<com.lovemanagement.domain.OperationLog> logs = logService.page(page, size);
        model.addAttribute("logs", logs);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", logs.getTotalPages());
        model.addAttribute("totalElements", logs.getTotalElements());
        model.addAttribute("hasPrevious", logs.hasPrevious());
        model.addAttribute("hasNext", logs.hasNext());
        model.addAttribute("prevUrl", "/admin/logs?page=" + (page - 1) + "&size=" + size);
        model.addAttribute("nextUrl", "/admin/logs?page=" + (page + 1) + "&size=" + size);
        model.addAttribute("pageUrlPrefix", "/admin/logs?size=" + size);
        return "admin/operation-logs";
    }
}
