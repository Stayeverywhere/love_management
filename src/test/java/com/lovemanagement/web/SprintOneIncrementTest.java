package com.lovemanagement.web;

import com.lovemanagement.domain.Member;
import com.lovemanagement.domain.MemberStatus;
import com.lovemanagement.repo.MemberRepository;
import com.lovemanagement.repo.OperationLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "classpath:db/test-baseline.sql")
class SprintOneIncrementTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private OperationLogRepository operationLogRepository;

    @Test
    void roleCanBeCopied() throws Exception {
        long before = operationLogRepository.count();
        mockMvc.perform(post("/admin/roles/2/copy").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/roles"));
        // 角色列表应出现"副本"
        mockMvc.perform(get("/admin/roles").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("副本")));
        assertThat(operationLogRepository.count()).isGreaterThan(before);
    }

    @Test
    void disabledRoleCannotLogin() throws Exception {
        // 禁用 pm 角色
        mockMvc.perform(post("/admin/roles/2/enabled").param("enabled", "false")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/roles"));
        // pm 账号应无法登录
        mockMvc.perform(formLogin("/login").user("pm").password("Pm123456"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void adminRoleCannotBeDisabled() throws Exception {
        mockMvc.perform(post("/admin/roles/1/enabled").param("enabled", "false")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/roles"));
        // admin 仍可登录（禁用被拒绝）
        mockMvc.perform(formLogin("/login").user("admin").password("Admin1234"))
                .andExpect(authenticated());
    }

    @Test
    void memberCanBeDisabledAndReenabled() throws Exception {
        Member m = new Member();
        m.setName("禁用测试成员");
        m.setStatus(MemberStatus.ACTIVE);
        m = memberRepository.save(m);

        mockMvc.perform(post("/admin/members/" + m.getId() + "/enabled").param("enabled", "false")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/members"));
        assertThat(memberRepository.findById(m.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.INACTIVE);

        mockMvc.perform(post("/admin/members/" + m.getId() + "/enabled").param("enabled", "true")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/members"));
        assertThat(memberRepository.findById(m.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void membersCanBeBulkImported() throws Exception {
        mockMvc.perform(post("/admin/members/import")
                        .param("names", "批量甲")
                        .param("names", "批量乙")
                        .param("names", "")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/members"));
        assertThat(memberRepository.findByName("批量甲")).isPresent();
        assertThat(memberRepository.findByName("批量乙")).isPresent();
    }

    @Test
    void personalPermissionsCanBeSetAndViewed() throws Exception {
        Member m = new Member();
        m.setName("权限测试成员");
        m.setStatus(MemberStatus.ACTIVE);
        m = memberRepository.save(m);

        mockMvc.perform(post("/admin/members/" + m.getId() + "/permissions")
                        .param("permissionIds", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/members"));

        mockMvc.perform(get("/admin/members/" + m.getId() + "/permissions")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("个人权限设置")));
    }

    @Test
    void operationLogsAreRecordedAndViewable() throws Exception {
        Member m = new Member();
        m.setName("日志测试成员");
        m.setStatus(MemberStatus.ACTIVE);
        m = memberRepository.save(m);

        // 禁用成员（触发日志）
        mockMvc.perform(post("/admin/members/" + m.getId() + "/enabled").param("enabled", "false")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/members"));

        // 全局操作日志页
        mockMvc.perform(get("/admin/logs").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("操作日志")));

        // 成员操作日志页
        mockMvc.perform(get("/admin/members/" + m.getId() + "/logs")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("日志测试成员")));
    }

    @Test
    void permissionChangesAreLogged() throws Exception {
        long before = operationLogRepository.count();
        mockMvc.perform(post("/admin/roles/2/permissions")
                        .param("codes", "MEMBER_VIEW")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/roles/2/permissions"));
        assertThat(operationLogRepository.count()).isGreaterThan(before);
    }
}
