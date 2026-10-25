package com.lovemanagement.web;

import com.lovemanagement.domain.AppRole;
import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskComment;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.AppRoleRepository;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.TaskCommentRepository;
import com.lovemanagement.repo.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class SprintTwoFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectTaskRepository taskRepository;
    @Autowired
    private TaskCommentRepository commentRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AppRoleRepository appRoleRepository;

    private Member createMember(String name) {
        Member member = new Member();
        member.setName(name);
        member.setStatus(MemberStatus.ACTIVE);
        return memberRepository.save(member);
    }

    private ProjectTask createTask(String title, UserAccount creator) {
        ProjectTask task = new ProjectTask();
        task.setTitle(title);
        task.setStatus(TaskStatus.TODO);
        task.setPriority("MEDIUM");
        task.setCreator(creator);
        return taskRepository.save(task);
    }

    @Test
    void taskCanBeCreatedWithPriorityAndDueDate() throws Exception {
        String dueDate = "2026-11-05";
        String location = mockMvc.perform(post("/pm/tasks")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("title", "带优先级与截止日期的任务")
                        .param("priority", "HIGH")
                        .param("dueDate", dueDate))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        ProjectTask saved = taskRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("带优先级与截止日期的任务"))
                .findFirst().orElseThrow();
        assertThat(location).contains("/pm/tasks/" + saved.getId() + "/assign");
        assertThat(saved.getPriority()).isEqualTo("HIGH");
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.parse(dueDate));
    }

    @Test
    void pmCanEditTaskTitlePriorityAndDueDate() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        ProjectTask task = createTask("待编辑任务", pm);

        mockMvc.perform(post("/pm/tasks/" + task.getId() + "/edit")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("title", "编辑后的任务")
                        .param("description", "更新描述")
                        .param("priority", "LOW")
                        .param("dueDate", "2026-12-01"))
                .andExpect(redirectedUrl("/pm/tasks"));

        ProjectTask updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("编辑后的任务");
        assertThat(updated.getDescription()).isEqualTo("更新描述");
        assertThat(updated.getPriority()).isEqualTo("LOW");
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void invalidTitleOnEditIsRejected() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        ProjectTask task = createTask("原标题", pm);

        mockMvc.perform(post("/pm/tasks/" + task.getId() + "/edit")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("title", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("任务标题不能为空")));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("原标题");
    }

    @Test
    void statusCanBeChangedAndInvalidStatusIsRejected() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        ProjectTask task = createTask("状态流转任务", pm);

        mockMvc.perform(post("/tasks/" + task.getId() + "/status")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("status", "IN_PROGRESS"))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        mockMvc.perform(post("/tasks/" + task.getId() + "/status")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("状态不合法")));
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void deletingTaskRemovesItsComments() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        ProjectTask task = createTask("待删除任务", pm);
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(pm);
        comment.setContent("这条评论应随任务删除");
        commentRepository.save(comment);
        assertThat(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())).hasSize(1);

        mockMvc.perform(post("/pm/tasks/" + task.getId() + "/delete")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/pm/tasks"));

        assertThat(taskRepository.findById(task.getId())).isEmpty();
        assertThat(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())).isEmpty();
    }

    @Test
    void memberFilterSeesOnlyOwnTasksAndPmSeesAll() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        UserAccount memberAccount = userAccountRepository.findByUsername("member").orElseThrow();
        Member boundMember = createMember("被分配成员");
        memberAccount.setMember(boundMember);
        userAccountRepository.save(memberAccount);

        ProjectTask own = createTask("分配给我的任务", pm);
        own.setAssignee(boundMember);
        taskRepository.save(own);
        createTask("别人的任务", pm);

        mockMvc.perform(get("/tasks?status=TODO").with(user("member").roles("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("分配给我的任务")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("别人的任务"))));

        mockMvc.perform(get("/pm/tasks").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("分配给我的任务")))
                .andExpect(content().string(containsString("别人的任务")));
    }

    @Test
    void pmCanFilterUnassignedTasks() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        Member assignee = createMember("未分配筛选成员");
        ProjectTask assigned = createTask("已分配任务", pm);
        assigned.setAssignee(assignee);
        taskRepository.save(assigned);
        createTask("未分配任务", pm);

        mockMvc.perform(get("/pm/tasks?assignee=none").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未分配任务")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("已分配任务"))));
    }

    @Test
    void memberListIsPaginated() throws Exception {
        for (int i = 1; i <= 12; i++) {
            createMember(String.format("分页成员%02d", i));
        }

        mockMvc.perform(get("/admin/members").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("共 12 条 · 第 1 / 2 页")))
                .andExpect(content().string(containsString("分页成员01")))
                .andExpect(content().string(containsString("分页成员10")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("分页成员11"))))
                .andExpect(content().string(containsString("下一页")))
                .andExpect(content().string(containsString("page-jump")))
                .andExpect(content().string(containsString("跳至")));

        mockMvc.perform(get("/admin/members?page=2").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("第 2 / 2 页")))
                .andExpect(content().string(containsString("分页成员11")))
                .andExpect(content().string(containsString("上一页")))
                .andExpect(content().string(containsString("/admin/members?size=10")));
    }

    @Test
    void memberListCanBeSearchedByName() throws Exception {
        createMember("搜索测试甲");
        createMember("搜索测试乙");

        mockMvc.perform(get("/admin/members").param("name", "搜索测试").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("搜索测试甲")))
                .andExpect(content().string(containsString("搜索测试乙")));

        // 模糊匹配：只含部分关键词的成员不出现
        mockMvc.perform(get("/admin/members").param("name", "测试甲").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("搜索测试甲")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("搜索测试乙"))));

        // 搜索框回显关键词，分页/跳转链接保留 name 参数
        mockMvc.perform(get("/admin/members").param("name", "测试甲").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("member-search")))
                .andExpect(content().string(containsString("value=\"测试甲\"")));

        // 无匹配结果时显示空态
        mockMvc.perform(get("/admin/members").param("name", "不存在的人").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("还没有成员")));
    }

    @Test
    void pmTaskListIsPaginated() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        for (int i = 1; i <= 12; i++) {
            createTask(String.format("分页任务%02d", i), pm);
        }

        // 任务列表按 id 倒序：第 1 页展示 12~03，不展示 01
        mockMvc.perform(get("/pm/tasks").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("共 12 条 · 第 1 / 2 页")))
                .andExpect(content().string(containsString("分页任务12")))
                .andExpect(content().string(containsString("分页任务03")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("分页任务01"))))
                .andExpect(content().string(containsString("下一页")));
    }

    @Test
    void memberCannotViewTaskNotAssignedToThem() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        UserAccount memberAccount = userAccountRepository.findByUsername("member").orElseThrow();
        memberAccount.setMember(createMember("无任务成员"));
        userAccountRepository.save(memberAccount);
        ProjectTask other = createTask("非本人任务", pm);

        mockMvc.perform(get("/tasks/" + other.getId()).with(user("member").roles("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void commentsAndRepliesArePersistedAndRendered() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        UserAccount memberAccount = userAccountRepository.findByUsername("member").orElseThrow();
        Member boundMember = createMember("评论成员");
        memberAccount.setMember(boundMember);
        userAccountRepository.save(memberAccount);
        ProjectTask task = createTask("讨论任务", pm);
        task.setAssignee(boundMember);
        taskRepository.save(task);

        mockMvc.perform(post("/tasks/" + task.getId() + "/comments")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("content", "请今天处理一下"))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));

        TaskComment top = commentRepository.findByTaskIdAndParentIdIsNullOrderByCreatedAtAsc(task.getId())
                .stream().findFirst().orElseThrow();

        mockMvc.perform(post("/tasks/" + task.getId() + "/comments")
                        .with(user("member").roles("MEMBER"))
                        .with(csrf())
                        .param("content", "好的，马上完成")
                        .param("parentId", String.valueOf(top.getId())))
                .andExpect(redirectedUrl("/tasks/" + task.getId()));

        mockMvc.perform(get("/tasks/" + task.getId()).with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("请今天处理一下")))
                .andExpect(content().string(containsString("好的，马上完成")));
    }

    @Test
    void emptyCommentIsRejected() throws Exception {
        UserAccount pm = userAccountRepository.findByUsername("pm").orElseThrow();
        ProjectTask task = createTask("空评论任务", pm);

        mockMvc.perform(post("/tasks/" + task.getId() + "/comments")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("content", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("评论内容不能为空")));
        assertThat(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())).isEmpty();
    }

    @Test
    void roleDataScopeCanBeSavedAndShownInPermissionsResult() throws Exception {
        mockMvc.perform(post("/admin/roles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "数据权限角色")
                        .param("description", "验证数据权限范围")
                        .param("dataScope", "TEAM"))
                .andExpect(redirectedUrl("/admin/roles"));

        AppRole role = appRoleRepository.findAll().stream()
                .filter(r -> r.getName().equals("数据权限角色"))
                .findFirst().orElseThrow();
        assertThat(role.getDataScope()).isEqualTo("TEAM");

        mockMvc.perform(get("/admin/roles/" + role.getId() + "/permissions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("本团队")));

        // 基线角色「团队成员」的 data_scope 为 SELF，权限配置结果页应展示「仅自己」
        AppRole baselineMemberRole = appRoleRepository.findAll().stream()
                .filter(r -> r.getName().equals("团队成员"))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/admin/roles/" + baselineMemberRole.getId() + "/permissions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("仅自己")));
    }

    @Test
    void invalidDataScopeIsRejected() throws Exception {
        mockMvc.perform(post("/admin/roles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "非法范围角色")
                        .param("dataScope", "BOGUS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("数据权限范围不合法")));
        assertThat(appRoleRepository.findAll()).noneMatch(r -> r.getName().equals("非法范围角色"));
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        mockMvc.perform(get("/profile").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pm")));

        mockMvc.perform(post("/profile")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("username", "pmchief")
                        .param("displayName", "首席项目经理"))
                .andExpect(redirectedUrl("/profile"));

        UserAccount updated = userAccountRepository.findByUsername("pmchief").orElseThrow();
        assertThat(updated.getDisplayName()).isEqualTo("首席项目经理");

        // 会话凭据已刷新，改账号后仍可正常访问
        mockMvc.perform(get("/profile").with(user("pmchief").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("首席项目经理")));
    }

    @Test
    void profileRejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/profile")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("username", "admin"))
                .andExpect(redirectedUrl("/profile"));

        assertThat(userAccountRepository.findByUsername("pm")).isPresent();
    }
}
