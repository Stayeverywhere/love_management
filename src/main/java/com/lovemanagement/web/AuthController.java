package com.lovemanagement.web;

import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.UserAccountRepository;
import com.lovemanagement.service.SecurityPolicy;
import com.lovemanagement.service.CurrentUserService;
import com.lovemanagement.service.PasswordPolicy;
import com.lovemanagement.service.PasswordService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final CurrentUserService currentUserService;
    private final PasswordService passwordService;
    private final UserAccountRepository userAccountRepository;

    public AuthController(CurrentUserService currentUserService, PasswordService passwordService,
                          UserAccountRepository userAccountRepository) {
        this.currentUserService = currentUserService;
        this.passwordService = passwordService;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/home";
        }
        return "login";
    }

    @GetMapping("/denied")
    public String denied() {
        return "denied";
    }

    @GetMapping("/password")
    public String passwordForm(Model model) {
        model.addAttribute("rule", PasswordPolicy.RULE);
        return "password";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model) {
        UserAccount account = currentUserService.requireCurrent();
        try {
            passwordService.changePassword(account, currentPassword, newPassword, confirmPassword);
            model.addAttribute("success", "密码已保存，下次可使用新密码登录。");
        } catch (PolicyException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        model.addAttribute("rule", PasswordPolicy.RULE);
        return "password";
    }

    @GetMapping("/profile")
    public String profileForm(Model model) {
        model.addAttribute("account", currentUserService.requireCurrent());
        return "profile";
    }

    @PostMapping("/profile/security")
    public String saveSecurity(@RequestParam(value = "securityQuestion", required = false) String question,
                               @RequestParam(value = "securityAnswer", required = false) String answer,
                               RedirectAttributes redirectAttributes) {
        try {
            String normalizedQuestion = SecurityPolicy.normalizeQuestion(question);
            String normalizedAnswer = SecurityPolicy.normalizeAnswer(answer);
            SecurityPolicy.assertConsistent(normalizedQuestion, normalizedAnswer);
            UserAccount account = currentUserService.requireCurrent();
            account.setSecurityQuestion(normalizedQuestion);
            account.setSecurityAnswer(normalizedAnswer);
            userAccountRepository.save(account);
            redirectAttributes.addFlashAttribute("success",
                    normalizedQuestion == null ? "密保已清空" : "密保已保存");
            return "redirect:/profile";
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/profile";
        }
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam String username,
                              @RequestParam(value = "displayName", required = false) String displayName,
                              RedirectAttributes redirectAttributes) {
        try {
            UserAccount account = currentUserService.requireCurrent();
            String name = username == null ? "" : username.trim();
            if (name.length() < 2 || name.length() > 32) {
                throw new PolicyException("账号长度需为 2-32 个字符");
            }
            UserAccount other = userAccountRepository.findByUsername(name).orElse(null);
            if (other != null && !other.getId().equals(account.getId())) {
                throw new PolicyException("该账号已被使用");
            }
            String display = displayName == null ? "" : displayName.trim();
            if (display.length() > 32) {
                throw new PolicyException("展示名不能超过 32 个字符");
            }
            account.setUsername(name);
            account.setDisplayName(display.isEmpty() ? name : display);
            userAccountRepository.save(account);
            // 会话中仍是旧账号的凭据，刷新为新账号，避免改账号后无法获取当前用户
            Authentication old = SecurityContextHolder.getContext().getAuthentication();
            UsernamePasswordAuthenticationToken refreshed =
                    new UsernamePasswordAuthenticationToken(account.getUsername(), old.getCredentials(), old.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(refreshed);
            redirectAttributes.addFlashAttribute("success", "个人资料已保存");
            return "redirect:/profile";
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/profile";
        }
    }
}
