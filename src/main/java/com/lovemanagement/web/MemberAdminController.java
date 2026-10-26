package com.lovemanagement.web;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.service.MemberAdminService;
import com.lovemanagement.service.OperationLogService;
import com.lovemanagement.service.PersonalPermissionService;
import com.lovemanagement.repo.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** 成员管理扩展：启用/禁用、批量导入、操作日志。 */
@Controller
@RequestMapping("/admin/members")
public class MemberAdminController {

    private final MemberAdminService memberAdminService;
    private final OperationLogService operationLogService;
    private final PersonalPermissionService personalPermissionService;
    private final MemberRepository memberRepository;

    public MemberAdminController(MemberAdminService memberAdminService,
                                 OperationLogService operationLogService,
                                 PersonalPermissionService personalPermissionService,
                                 MemberRepository memberRepository) {
        this.memberAdminService = memberAdminService;
        this.operationLogService = operationLogService;
        this.personalPermissionService = personalPermissionService;
        this.memberRepository = memberRepository;
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable Long id,
                             @RequestParam boolean enabled,
                             RedirectAttributes redirectAttributes) {
        try {
            memberAdminService.setEnabled(id, enabled);
            redirectAttributes.addFlashAttribute("success", enabled ? "成员已启用" : "成员已禁用");
            return "redirect:/admin/members";
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/members";
        }
    }

    @GetMapping("/import")
    public String importForm(Model model) {
        return "admin/member-import";
    }

    @PostMapping("/import")
    public String importMembers(@RequestParam(value = "names", required = false) List<String> names,
                                @RequestParam(value = "emails", required = false) List<String> emails,
                                RedirectAttributes redirectAttributes) {
        List<String> safeNames = names == null ? List.of() : names;
        List<String> safeEmails = emails == null ? List.of() : emails;
        int count = memberAdminService.bulkImport(safeNames, safeEmails).size();
        redirectAttributes.addFlashAttribute("success", "成功导入 " + count + " 名成员");
        return "redirect:/admin/members";
    }

    @GetMapping("/{id}/logs")
    public String memberLogs(@PathVariable Long id, Model model,
                             @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                             @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        Page<com.lovemanagement.domain.OperationLog> logs = operationLogService.pageByTarget("MEMBER", id, page, size);
        model.addAttribute("logs", logs);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", logs.getTotalPages());
        model.addAttribute("totalElements", logs.getTotalElements());
        model.addAttribute("hasPrevious", logs.hasPrevious());
        model.addAttribute("hasNext", logs.hasNext());
        model.addAttribute("prevUrl", "/admin/members/" + id + "/logs?page=" + (page - 1) + "&size=" + size);
        model.addAttribute("nextUrl", "/admin/members/" + id + "/logs?page=" + (page + 1) + "&size=" + size);
        model.addAttribute("pageUrlPrefix", "/admin/members/" + id + "/logs?size=" + size);
        return "admin/member-logs";
    }

    @GetMapping("/{id}/permissions")
    public String permissionsForm(@PathVariable Long id, Model model,
                                  @RequestParam(value = "error", required = false) String error) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new PolicyException("成员不存在"));
        model.addAttribute("member", member);
        model.addAttribute("allPermissions", personalPermissionService.allPermissions());
        model.addAttribute("selected", personalPermissionService.getPermissionIds(id));
        model.addAttribute("error", error);
        return "admin/member-permissions";
    }

    @PostMapping("/{id}/permissions")
    public String permissionsSave(@PathVariable Long id,
                                  @RequestParam(value = "permissionIds", required = false) List<Long> permissionIds,
                                  RedirectAttributes redirectAttributes) {
        try {
            Member member = memberRepository.findById(id)
                    .orElseThrow(() -> new PolicyException("成员不存在"));
            List<Long> safeIds = permissionIds == null ? List.of() : permissionIds;
            personalPermissionService.save(id, safeIds);
            operationLogService.log("个人权限设置", "MEMBER", id,
                    "为成员「" + member.getName() + "」设置个人权限（" + safeIds.size() + " 项）");
            redirectAttributes.addFlashAttribute("success", "个人权限已保存");
            return "redirect:/admin/members";
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/members";
        }
    }
}
