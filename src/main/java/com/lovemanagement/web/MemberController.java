package com.lovemanagement.web;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.service.MemberService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "name", required = false) String name,
                       @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        String keyword = name == null ? "" : name.trim();
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 50));
        org.springframework.data.domain.Page<Member> memberPage = memberService.pageByName(keyword, pageable);
        model.addAttribute("members", memberPage.getContent());
        model.addAttribute("searchName", keyword);
        int current = memberPage.getNumber() + 1;
        int pageSize = memberPage.getSize();
        model.addAttribute("page", current);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalPages", memberPage.getTotalPages());
        model.addAttribute("totalElements", memberPage.getTotalElements());
        model.addAttribute("hasPrevious", memberPage.hasPrevious());
        model.addAttribute("hasNext", memberPage.hasNext());
        String filters = keyword.isEmpty() ? "" : "&name=" + keyword;
        model.addAttribute("prevUrl", "/admin/members?page=" + (current - 1) + "&size=" + pageSize + filters);
        model.addAttribute("nextUrl", "/admin/members?page=" + (current + 1) + "&size=" + pageSize + filters);
        model.addAttribute("pageUrlPrefix", "/admin/members?size=" + pageSize + filters);
        return "admin/members";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("member")) {
            Member member = new Member();
            member.setStatus(MemberStatus.ACTIVE);
            model.addAttribute("member", member);
        }
        model.addAttribute("statuses", MemberStatus.values());
        return "admin/member-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("member", memberService.get(id));
        model.addAttribute("statuses", MemberStatus.values());
        return "admin/member-form";
    }

    @PostMapping
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String name,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String position,
                       @RequestParam MemberStatus status,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            memberService.save(id, name, email, phone, position, status);
            redirectAttributes.addFlashAttribute("success", id == null ? "成员已添加" : "成员信息已保存");
            return "redirect:/admin/members";
        } catch (PolicyException ex) {
            Member member = new Member();
            member.setId(id);
            member.setName(name);
            member.setEmail(email);
            member.setPhone(phone);
            member.setPosition(position);
            member.setStatus(status);
            model.addAttribute("member", member);
            model.addAttribute("statuses", MemberStatus.values());
            model.addAttribute("error", ex.getMessage());
            return "admin/member-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            memberService.delete(id);
            redirectAttributes.addFlashAttribute("success", "成员已移除");
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/members";
    }
}
