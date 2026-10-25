package com.lovemanagement.service;

import com.lovemanagement.domain.PolicyException;

/** 密保问题/答案校验规则。 */
public final class SecurityPolicy {

    private SecurityPolicy() {
    }

    public static String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String value = question.trim();
        if (value.length() < 2 || value.length() > 100) {
            throw new PolicyException("密保问题长度须为 2 到 100 个字符");
        }
        return value;
    }

    public static String normalizeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String value = answer.trim();
        if (value.length() < 2 || value.length() > 100) {
            throw new PolicyException("密保答案长度须为 2 到 100 个字符");
        }
        return value;
    }

    /** 问题与答案须同时设置或同时清空。 */
    public static void assertConsistent(String question, String answer) {
        if ((question == null) != (answer == null)) {
            throw new PolicyException("密保问题与答案须同时填写或同时清空");
        }
    }
}
