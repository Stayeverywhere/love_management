package com.lovemanagement.web;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.domain.ProjectTask;
import com.lovemanagement.domain.TaskStatus;
import com.lovemanagement.domain.UserAccount;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.ProjectTaskRepository;
import com.lovemanagement.repo.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 巡检：真实登录（非 @WithMockUser）后用同一会话逐页访问所有 GET 页面，
 * 跟随重定向，收集所有非 200 页面，任何 500/400 都会列出。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class AllPagesSmokeTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectTaskRepository taskRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private MemberRepository memberRepository;

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user(username).password(password))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession();
    }

    /** 访问页面并跟随 3xx 重定向，返回最终状态码 + 最终 URL。 */
    private String[] follow(MockHttpSession session, String page) throws Exception {
        String url = page;
        int code = 0;
        String body = "";
        for (int hop = 0; hop < 5; hop++) {
            MvcResult result = mockMvc.perform(get(url).session(session)).andReturn();
            code = result.getResponse().getStatus();
            if (code >= 200 && code < 300) {
                body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                return new String[]{String.valueOf(code), url, body};
            }
            if (code >= 300 && code < 400) {
                String loc = result.getResponse().getRedirectedUrl();
                if (loc == null) {
                    return new String[]{String.valueOf(code), url, ""};
                }
                url = loc.startsWith("http") ? loc.substring(loc.indexOf('/', 8)) : loc;
                continue;
            }
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            return new String[]{String.valueOf(code), url, body};
        }
        return new String[]{String.valueOf(code), url, ""};
    }

    @Test
    void adminEveryPageReturns200() throws Exception {
        MockHttpSession session = login("admin", "Admin1234");
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        ProjectTask task = new ProjectTask();
        task.setTitle("巡检任务");
        task.setStatus(TaskStatus.TODO);
        task.setPriority("MEDIUM");
        task.setCreator(admin);
        task = taskRepository.save(task);

        String[] pages = {
                "/", "/home",
                "/pm/dashboard", "/pm/tasks", "/pm/tasks?status=TODO", "/pm/tasks?assignee=none",
                "/pm/tasks?page=2&size=5", "/pm/tasks/new",
                "/pm/tasks/" + task.getId() + "/edit", "/pm/tasks/" + task.getId() + "/assign",
                "/tasks", "/tasks/" + task.getId(), "/tasks/" + task.getId() + "/discussions",
                "/pm/trends", "/pm/trends?granularity=week", "/pm/trends?granularity=month",
                "/pm/reports",
                "/admin/members", "/admin/members?name=测", "/admin/members?page=2&size=5",
                "/admin/members/new", "/admin/roles", "/profile", "/password", "/stats/personal"
        };
        List<String> failures = new ArrayList<>();
        for (String page : pages) {
            String[] r = follow(session, page);
            if (!r[0].equals("200")) {
                failures.add(page + " -> " + r[0] + " (最终 " + r[1] + ")");
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void memberEveryPageReturns200() throws Exception {
        MockHttpSession session = login("member", "Member123");
        UserAccount member = userAccountRepository.findByUsername("member").orElseThrow();
        Member m = new Member();
        m.setName("巡检成员");
        m.setStatus(MemberStatus.ACTIVE);
        member.setMember(memberRepository.save(m));
        userAccountRepository.save(member);
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        ProjectTask task = new ProjectTask();
        task.setTitle("巡检成员任务");
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority("LOW");
        task.setCreator(admin);
        task.setAssignee(m);
        task = taskRepository.save(task);

        String[] pages = {
                "/", "/home", "/tasks", "/tasks?status=IN_PROGRESS",
                "/tasks/" + task.getId(), "/tasks/" + task.getId() + "/discussions",
                "/profile", "/password", "/stats/personal"
        };
        List<String> failures = new ArrayList<>();
        for (String page : pages) {
            String[] r = follow(session, page);
            if (!r[0].equals("200")) {
                failures.add(page + " -> " + r[0] + " (最终 " + r[1] + ")");
            }
        }
        assertThat(failures).isEmpty();

        // 成员访问 PM 页面应 403（越权拒绝），不应 500
        String[] denied = follow(session, "/pm/dashboard");
        assertThat(denied[0]).isIn("403", "404");
    }
}
