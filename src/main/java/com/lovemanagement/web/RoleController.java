package com.lovemanagement.web;

import com.lovemanagement.domain.AppRole;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.service.RoleNamePolicy;
import com.lovemanagement.service.RoleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("roles", roleService.list());
        return "admin/roles";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("role")) {
            model.addAttribute("role", new AppRole());
        }
        model.addAttribute("rule", RoleNamePolicy.RULE);
        return "admin/role-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("role", roleService.get(id));
        model.addAttribute("dataScope", roleService.get(id).getDataScope());
        model.addAttribute("rule", RoleNamePolicy.RULE);
        return "admin/role-form";
    }

    @PostMapping
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String name,
                       @RequestParam(required = false) String description,
            @RequestParam(required = false) String dataScope,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            roleService.save(id, name, description, dataScope);
            redirectAttributes.addFlashAttribute("success", "角色配置已保存");
            return "redirect:/admin/roles";
        } catch (PolicyException ex) {
            AppRole role = new AppRole();
            role.setId(id);
            role.setName(name);
            role.setDescription(description);
            model.addAttribute("role", role);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("dataScope", dataScope);
            model.addAttribute("rule", RoleNamePolicy.RULE);
            return "admin/role-form";
        }
    }

    @GetMapping("/{id}/permissions")
    public String permissions(@PathVariable Long id, Model model) {
        AppRole role = roleService.get(id);
        model.addAttribute("role", role);
        model.addAttribute("permissions", roleService.allPermissions());
        model.addAttribute("selected", role.getPermissions().stream().map(item -> item.getCode()).toList());
        return "admin/role-permissions";
    }

    @PostMapping("/{id}/permissions")
    public String savePermissions(@PathVariable Long id,
                                  @RequestParam(required = false) List<String> codes,
                                  RedirectAttributes redirectAttributes) {
        try {
            roleService.savePermissions(id, codes);
            redirectAttributes.addFlashAttribute("success", "权限配置已保存");
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/roles/" + id + "/permissions";
    }

    @PostMapping("/{id}/copy")
    public String copy(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            AppRole copied = roleService.copy(id);
            redirectAttributes.addFlashAttribute("success", "已复制角色「" + copied.getName() + "」");
            return "redirect:/admin/roles";
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/roles";
        }
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable Long id,
                             @RequestParam boolean enabled,
                             RedirectAttributes redirectAttributes) {
        try {
            roleService.setEnabled(id, enabled);
            redirectAttributes.addFlashAttribute("success", enabled ? "角色已启用" : "角色已禁用");
            return "redirect:/admin/roles";
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/roles";
        }
    }
}
