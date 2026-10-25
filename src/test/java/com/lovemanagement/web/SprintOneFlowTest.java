package com.lovemanagement.web;

import com.lovemanagement.domain.AppRole;
import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.AppRoleRepository;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.UserAccountRepository;
import com.lovemanagement.service.DashboardService;
import com.lovemanagement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class SprintOneFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppRoleRepository appRoleRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProjectTaskRepository taskRepository;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private TaskService taskService;

    @Test
    void validLoginSucceedsAndWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("Admin1234"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/home"));

        mockMvc.perform(formLogin("/login").user("admin").password("wrong-pass"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("账号或密码不正确")));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("pm").password("Pm123456"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        mockMvc.perform(get("/pm/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void passwordChangeIsSavedAndFollowsRules() throws Exception {
        mockMvc.perform(post("/password")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("currentPassword", "Admin1234")
                        .param("newPassword", "short")
                        .param("confirmPassword", "short"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("密码长度须为 8 到 20 位")));

        mockMvc.perform(post("/password")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("currentPassword", "Admin1234")
                        .param("newPassword", "Newpass123")
                        .param("confirmPassword", "Newpass123"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("密码已保存")));

        assertThat(passwordEncoder.matches("Newpass123",
                userAccountRepository.findByUsername("admin").orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void roleNameIsValidatedAndConfigurationCanBeReadBack() throws Exception {
        mockMvc.perform(post("/admin/roles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "坏 名称")
                        .param("description", "说明"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("角色名称")));

        mockMvc.perform(post("/admin/roles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "联调角色")
                        .param("description", "用于保存测试"))
                .andExpect(redirectedUrl("/admin/roles"));

        AppRole saved = appRoleRepository.findAll().stream()
                .filter(role -> "联调角色".equals(role.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("用于保存测试");

        mockMvc.perform(get("/admin/roles").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("联调角色")));
    }

    @Test
    void permissionSelectionIsPersisted() throws Exception {
        AppRole role = appRoleRepository.findAll().stream()
                .filter(item -> "团队成员".equals(item.getName()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/admin/roles/" + role.getId() + "/permissions")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("codes", "TASK_VIEW")
                        .param("codes", "DASHBOARD_VIEW"))
                .andExpect(redirectedUrl("/admin/roles/" + role.getId() + "/permissions"));

        AppRole reloaded = appRoleRepository.findById(role.getId()).orElseThrow();
        Set<String> codes = reloaded.getPermissions().stream().map(item -> item.getCode()).collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder("TASK_VIEW", "DASHBOARD_VIEW");

        mockMvc.perform(get("/admin/roles/" + role.getId() + "/permissions").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("当前选择")));
    }

    @Test
    void memberListAndCrudPersistChanges() throws Exception {
        createMember("陈一", "ACTIVE");

        mockMvc.perform(get("/admin/members").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("陈一")));

        mockMvc.perform(post("/admin/members")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "赵")
                        .param("status", "ACTIVE"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("成员姓名长度")));

        mockMvc.perform(post("/admin/members")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "赵六")
                        .param("email", "zhaoliu@example.com")
                        .param("phone", "13900000006")
                        .param("position", "设计")
                        .param("status", "ACTIVE"))
                .andExpect(redirectedUrl("/admin/members"));

        Member created = memberRepository.findAll().stream()
                .filter(member -> "赵六".equals(member.getName()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/admin/members")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("id", String.valueOf(created.getId()))
                        .param("name", "赵六六")
                        .param("email", "zhaoliu@example.com")
                        .param("phone", "13900000006")
                        .param("position", "设计负责人")
                        .param("status", "INACTIVE"))
                .andExpect(redirectedUrl("/admin/members"));

        Member updated = memberRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("赵六六");
        assertThat(updated.getStatus()).isEqualTo(MemberStatus.INACTIVE);

        mockMvc.perform(post("/admin/members/" + created.getId() + "/delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/members"));
        assertThat(memberRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void taskCanBeSavedAssignedAndReadByAssignee() throws Exception {
        Member chen = createMember("陈一", "ACTIVE");
        Member wang = createMember("王五", "INACTIVE");

        mockMvc.perform(post("/pm/tasks")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("title", " "))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("任务标题不能为空")));

        String location = mockMvc.perform(post("/pm/tasks")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("title", "编写登录测试")
                        .param("description", "覆盖成功和失败"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        ProjectTask saved = taskRepository.findAll().stream()
                .filter(task -> "编写登录测试".equals(task.getTitle()))
                .findFirst()
                .orElseThrow();
        assertThat(location).contains("/pm/tasks/" + saved.getId() + "/assign");

        mockMvc.perform(get("/pm/tasks/" + saved.getId() + "/assign").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("陈一")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("王五"))));

        mockMvc.perform(post("/pm/tasks/" + saved.getId() + "/assign")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .param("memberId", String.valueOf(chen.getId())))
                .andExpect(redirectedUrl("/tasks/" + saved.getId()));

        ProjectTask assigned = taskRepository.findById(saved.getId()).orElseThrow();
        assertThat(assigned.getAssignee().getName()).isEqualTo("陈一");
        assertThat(assigned.getAssignedAt()).isNotNull();

        mockMvc.perform(get("/tasks/" + saved.getId()).with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("编写登录测试")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("陈一")));

        UserAccount memberAccount = userAccountRepository.findByUsername("member").orElseThrow();
        memberAccount.setMember(chen);
        userAccountRepository.save(memberAccount);

        ProjectTask ownTask = taskRepository.findAll().stream()
                .filter(task -> "编写登录测试".equals(task.getTitle()))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(get("/tasks/" + ownTask.getId()).with(user("member").roles("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("编写登录测试")));

        ProjectTask otherTask = taskService.create("他人任务", "用于核对成员可见范围", memberAccount);
        mockMvc.perform(get("/tasks/" + otherTask.getId()).with(user("member").roles("MEMBER")))
                .andExpect(status().isForbidden());
    }

    private Member createMember(String name, String status) {
        Member member = new Member();
        member.setName(name);
        member.setStatus(MemberStatus.valueOf(status));
        return memberRepository.save(member);
    }

    @Test
    void dashboardAndTrendMatchStoredData() throws Exception {
        DashboardService.DashboardSnapshot board = dashboardService.snapshot();
        assertThat(board.totalMembers()).isEqualTo(memberRepository.count());
        assertThat(board.activeMembers()).isEqualTo(memberRepository.countByStatus(MemberStatus.ACTIVE));
        assertThat(board.totalTasks()).isEqualTo(taskRepository.count());
        assertThat(board.assignedTasks()).isEqualTo(taskRepository.countByAssigneeIsNotNull());
        assertThat(board.unassignedTasks()).isEqualTo(taskRepository.countByAssigneeIsNull());

        mockMvc.perform(get("/pm/dashboard").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"metric-tasks\">" + board.totalTasks())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"metric-members\">" + board.totalMembers())));

        DashboardService.TrendSeries series = dashboardService.last7Days();
        assertThat(series.labels()).hasSize(7);
        LocalDateTime from = LocalDate.now().minusDays(6).atStartOfDay();
        long expected = taskRepository.countByCreatedAtGreaterThanEqual(from);
        long actual = series.values().stream().mapToLong(Long::longValue).sum();
        assertThat(actual).isEqualTo(expected);

        mockMvc.perform(get("/pm/trends").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(series.labels().get(0))));
    }
}
