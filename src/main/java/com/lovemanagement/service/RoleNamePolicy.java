package com.lovemanagement.service;

import com.lovemanagement.domain.PolicyException;

import java.util.regex.Pattern;

public final class RoleNamePolicy {

    public static final String RULE = "角色名称必填，长度为 2 到 20 个字符，仅允许中文、字母、数字和下划线。";
    private static final Pattern PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_]{2,20}$");

    private RoleNamePolicy() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PolicyException("角色名称不能为空");
        }
        String name = raw.trim();
        if (!PATTERN.matcher(name).matches()) {
            throw new PolicyException(RULE);
        }
        return name;
    }
}
