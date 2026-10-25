package com.lovemanagement.service;

import com.lovemanagement.domain.PolicyException;

public final class TaskTitlePolicy {

    public static final String RULE = "任务标题必填，长度为 2 到 50 个字符。";

    private TaskTitlePolicy() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PolicyException("任务标题不能为空");
        }
        String title = raw.trim();
        if (title.length() < 2 || title.length() > 50) {
            throw new PolicyException(RULE);
        }
        return title;
    }
}
