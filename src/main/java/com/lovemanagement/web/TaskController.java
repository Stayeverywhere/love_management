package com.lovemanagement.web;

import com.lovemanagement.domain.AccountRole;
import com.lovemanagement.domain.PolicyException;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.service.CommentView;
import com.lovemanagement.service.CurrentUserService;
import com.lovemanagement.service.MemberService;
import com.lovemanagement.service.TaskService;
import com.lovemanagement.service.TaskTitlePolicy;
import jakarta.servlet.http.HttpServletResponse;
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
@RequestMapping
public class TaskController {

    private final TaskService taskService;
    private final MemberService memberService;
    private final CurrentUserService currentUserService;

    public TaskController(TaskService taskService, MemberService memberService,
                          CurrentUserService currentUserService) {
        this.taskService = taskService;
        this.memberService = memberService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/tasks")
    public String mine(Model model, @RequestParam(value = "status", required = false) String status,
                       HttpServletResponse response) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            List<ProjectTask> tasks;
            TaskStatus selected = parseStatus(status);
            if (me.getAccountRole() == AccountRole.MEMBER) {
                tasks = selected == null
                        ? taskService.listForMember(me.getMember() == null ? null : me.getMember().getId())
                        : taskService.listByStatusAndMember(selected, me.getMember() == null ? null : me.getMember().getId());
            } else {
                tasks = selected == null ? taskService.listAll() : taskService.listByStatus(selected);
            }
            model.addAttribute("tasks", tasks);
            model.addAttribute("selected", status == null ? "" : status);
            return "task/list";
        } catch (PolicyException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            model.addAttribute("error", ex.getMessage());
            return "task/list";
        }
    }

    @GetMapping("/tasks/{id}")
    public String detail(@PathVariable Long id, Model model, HttpServletResponse response) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.getVisible(id, me);
            model.addAttribute("task", task);
            model.addAttribute("canManage", canManageTask(me, task));
            model.addAttribute("comments", taskService.commentTree(task.getId()));
            return "task/detail";
        } catch (PolicyException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    @GetMapping("/tasks/{id}/discussions")
    public String discussions(@PathVariable Long id, Model model, HttpServletResponse response) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.getVisible(id, me);
            model.addAttribute("task", task);
            model.addAttribute("canManage", canManageTask(me, task));
            model.addAttribute("timeline", taskService.commentTimeline(task.getId()));
            return "task/discussions";
        } catch (PolicyException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            model.addAttribute("error", ex.getMessage());
            return "task/discussions";
        }
    }

    @PostMapping("/tasks/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam String status,
                               Model model, RedirectAttributes redirectAttributes) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.getVisible(id, me);
            if (!taskService.canManageStatus(task, me)) {
                throw new PolicyException("无权修改该任务状态");
            }
            TaskStatus selected;
            try {
                selected = TaskStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new PolicyException("状态不合法");
            }
            taskService.changeStatus(task, selected);
            redirectAttributes.addFlashAttribute("success", "状态已更新为 " + selected.getLabel());
            return "redirect:/tasks/" + id;
        } catch (PolicyException ex) {
            if (ex.getMessage().equals("状态不合法")) {
                ProjectTask task = taskService.get(id);
                model.addAttribute("task", task);
                model.addAttribute("canManage", canManageTask(me, task));
                model.addAttribute("comments", taskService.commentTree(task.getId()));
                model.addAttribute("error", ex.getMessage());
                return "task/detail";
            }
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/tasks/" + id;
        }
    }

    @PostMapping("/tasks/{id}/comments")
    public String addComment(@PathVariable Long id, @RequestParam String content,
                             @RequestParam(value = "parentId", required = false) Long parentId,
                             Model model, RedirectAttributes redirectAttributes) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.getVisible(id, me);
            taskService.addComment(task, me, content, parentId);
            redirectAttributes.addFlashAttribute("success", "评论已发送");
            return "redirect:/tasks/" + id;
        } catch (PolicyException ex) {
            if (ex.getMessage().equals("任务不存在") || ex.getMessage().equals("只能查看分配给自己的任务")) {
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/tasks";
            }
            model.addAttribute("task", taskService.get(id));
            model.addAttribute("comments", taskService.commentTree(id));
            model.addAttribute("canManage", true);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    @GetMapping("/pm/tasks")
    public String manage(Model model,
                         @RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "assignee", required = false) String assignee,
                         @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                         @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        TaskStatus selected = parseStatus(status);
        boolean unassigned = "none".equalsIgnoreCase(assignee);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 50),
                        org.springframework.data.domain.Sort.by("id").descending());
        org.springframework.data.domain.Page<ProjectTask> taskPage;
        if (unassigned) {
            taskPage = taskService.pageUnassigned(pageable);
        } else if (selected == null) {
            taskPage = taskService.pageAll(pageable);
        } else {
            taskPage = taskService.pageByStatus(selected, pageable);
        }
        model.addAttribute("tasks", taskPage.getContent());
        model.addAttribute("selected", status == null ? "" : status);
        model.addAttribute("assignee", assignee == null ? "" : assignee);
        addPagination(model, taskPage, "/pm/tasks", status, assignee, size);
        model.addAttribute("rule", TaskTitlePolicy.RULE);
        return "pm/tasks";
    }

    @GetMapping("/pm/tasks/new")
    public String createForm(Model model) {
        model.addAttribute("title", "");
        model.addAttribute("description", "");
        model.addAttribute("rule", TaskTitlePolicy.RULE);
        return "pm/task-form";
    }

    @PostMapping("/pm/tasks")
    public String create(@RequestParam String title,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "priority", required = false) String priority,
                         @RequestParam(value = "dueDate", required = false) String dueDate,
                         @RequestParam(value = "remindAt", required = false) String remindAt,
                         Model model, RedirectAttributes redirectAttributes) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.create(title, description, priority, dueDate, remindAt, me);
            redirectAttributes.addFlashAttribute("success", "任务已保存");
            return "redirect:/pm/tasks/" + task.getId() + "/assign";
        } catch (PolicyException ex) {
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("priority", priority == null ? "MEDIUM" : priority);
            model.addAttribute("dueDate", dueDate);
            model.addAttribute("remindAt", remindAt);
            model.addAttribute("rule", TaskTitlePolicy.RULE);
            model.addAttribute("error", ex.getMessage());
            return "pm/task-form";
        }
    }

    @GetMapping("/pm/tasks/{id}/assign")
    public String assignForm(@PathVariable Long id, Model model) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.getVisible(id, me);
            model.addAttribute("task", task);
            model.addAttribute("candidates", memberService.assignable());
            return "pm/task-assign";
        } catch (PolicyException ex) {
            model.addAttribute("error", ex.getMessage());
            return "pm/task-assign";
        }
    }

    @PostMapping("/pm/tasks/{id}/assign")
    public String assign(@PathVariable Long id,
                         @RequestParam(value = "memberId", required = false) Long memberId,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.assign(id, memberId);
            redirectAttributes.addFlashAttribute("success", "任务已分配");
            return "redirect:/tasks/" + task.getId();
        } catch (PolicyException ex) {
            model.addAttribute("task", taskService.get(id));
            model.addAttribute("candidates", memberService.assignable());
            model.addAttribute("error", ex.getMessage());
            return "pm/task-assign";
        }
    }

    @GetMapping("/pm/tasks/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.get(id);
            taskService.assertCanEdit(task, me);
            model.addAttribute("task", task);
            model.addAttribute("rule", TaskTitlePolicy.RULE);
            return "pm/task-edit";
        } catch (PolicyException ex) {
            model.addAttribute("error", ex.getMessage());
            return "pm/task-edit";
        }
    }

    @PostMapping("/pm/tasks/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam String title,
                       @RequestParam(value = "description", required = false) String description,
                       @RequestParam(value = "priority", required = false) String priority,
                       @RequestParam(value = "dueDate", required = false) String dueDate,
                       @RequestParam(value = "remindAt", required = false) String remindAt,
                       Model model, RedirectAttributes redirectAttributes) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.get(id);
            taskService.assertCanEdit(task, me);
            taskService.update(task, title, description, priority, dueDate, remindAt);
            redirectAttributes.addFlashAttribute("success", "任务已保存");
            return "redirect:/pm/tasks";
        } catch (PolicyException ex) {
            if (ex.getMessage().equals("任务不存在") || ex.getMessage().equals("只能编辑自己创建的任务")) {
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/pm/tasks";
            }
            ProjectTask task = taskService.get(id);
            model.addAttribute("task", task);
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("priority", priority == null ? "MEDIUM" : priority);
            model.addAttribute("dueDate", dueDate);
            model.addAttribute("remindAt", remindAt);
            model.addAttribute("rule", TaskTitlePolicy.RULE);
            model.addAttribute("error", ex.getMessage());
            return "pm/task-edit";
        }
    }

    @PostMapping("/pm/tasks/{id}/delete")
    public String delete(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        UserAccount me = currentUserService.requireCurrent();
        try {
            ProjectTask task = taskService.get(id);
            taskService.assertCanDelete(task, me);
            taskService.delete(id);
            redirectAttributes.addFlashAttribute("success", "任务已删除");
            return "redirect:/pm/tasks";
        } catch (PolicyException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/pm/tasks";
        }
    }

    private void addPagination(Model model, org.springframework.data.domain.Page<?> page,
                               String baseUrl, String status, String assignee, int size) {
        int current = page.getNumber() + 1;
        String filters = "";
        if (status != null && !status.isEmpty()) {
            filters += "&status=" + status;
        }
        if (assignee != null && !assignee.isEmpty()) {
            filters += "&assignee=" + assignee;
        }
        model.addAttribute("page", current);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("totalElements", page.getTotalElements());
        model.addAttribute("hasPrevious", page.hasPrevious());
        model.addAttribute("hasNext", page.hasNext());
        model.addAttribute("prevUrl", baseUrl + "?page=" + (current - 1) + "&size=" + size + filters);
        model.addAttribute("nextUrl", baseUrl + "?page=" + (current + 1) + "&size=" + size + filters);
        model.addAttribute("pageUrlPrefix", baseUrl + "?size=" + size + filters);
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return TaskStatus.valueOf(status.trim().toUpperCase());
    }

    private boolean canManageTask(UserAccount me, ProjectTask task) {
        if (me.getAccountRole() != AccountRole.MEMBER) {
            return true;
        }
        if (task.getCreator() != null && task.getCreator().getId().equals(me.getId())) {
            return true;
        }
        return task.getAssignee() != null && me.getMember() != null
                && task.getAssignee().getId().equals(me.getMember().getId());
    }
}
