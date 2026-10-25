package com.lovemanagement.service;

import com.lovemanagement.domain.PolicyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyTest {

    @Test
    void passwordRulesRejectInvalidValues() {
        assertThatThrownBy(() -> PasswordPolicy.check("admin", "short1")).isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("admin", "longpassword")).isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("admin", "Admin 1234")).isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("admin1234", "Admin1234"))
                .isInstanceOf(PolicyException.class)
                .hasMessage("密码不能与账号相同");
        PasswordPolicy.check("admin", "Admin1234");
    }

    @Test
    void roleNameIsRequiredAndLimited() {
        assertThatThrownBy(() -> RoleNamePolicy.normalize(" ")).hasMessage("角色名称不能为空");
        assertThatThrownBy(() -> RoleNamePolicy.normalize("a")).isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> RoleNamePolicy.normalize("角色 名称")).isInstanceOf(PolicyException.class);
        assertThat(RoleNamePolicy.normalize(" 项目_经理1 ")).isEqualTo("项目_经理1");
    }

    @Test
    void taskTitleIsRequired() {
        assertThatThrownBy(() -> TaskTitlePolicy.normalize("  ")).hasMessage("任务标题不能为空");
        assertThatThrownBy(() -> TaskTitlePolicy.normalize("题")).isInstanceOf(PolicyException.class);
        assertThat(TaskTitlePolicy.normalize("  编写登录测试  ")).isEqualTo("编写登录测试");
    }
}
