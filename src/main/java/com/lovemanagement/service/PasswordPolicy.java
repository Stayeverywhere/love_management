package com.lovemanagement.service;

import com.lovemanagement.domain.PolicyException;

public final class PasswordPolicy {

    public static final String RULE = "密码长度为 8 到 20 位，必须同时包含字母和数字，不能包含空格，且不能与账号相同。";

    private PasswordPolicy() {
    }

    public static void check(String username, String password) {
        if (password == null || password.isBlank()) {
            throw new PolicyException("密码不能为空");
        }
        if (password.length() < 8 || password.length() > 20) {
            throw new PolicyException("密码长度须为 8 到 20 位");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new PolicyException("密码不能包含空格");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new PolicyException("密码须同时包含字母和数字");
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            throw new PolicyException("密码不能与账号相同");
        }
    }
}
