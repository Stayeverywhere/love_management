package com.lovemanagement.web;

import com.lovemanagement.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {

    private final CurrentUserService currentUserService;

    public CurrentUserAdvice(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @ModelAttribute("currentUser")
    public Object currentUser() {
        return currentUserService.currentOrNull();
    }

    /** Thymeleaf 3.1 默认禁用 #request，这里手动注入 requestURI 供侧边栏激活态判断。 */
    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
