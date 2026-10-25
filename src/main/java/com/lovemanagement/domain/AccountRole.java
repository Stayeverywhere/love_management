package com.lovemanagement.domain;

public enum AccountRole {
    ADMIN("管理员"),
    PROJECT_MANAGER("项目经理"),
    MEMBER("团队成员");

    private final String label;

    AccountRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
