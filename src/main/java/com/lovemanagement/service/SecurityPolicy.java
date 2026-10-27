package com.lovemanagement.service;

import com.lovemanagement.domain.PolicyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        return hash(value);
    }

    /** 密保答案以加盐 SHA-256 摘要存储，避免明文落库、不以明文展示。 */
    private static String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(("love-secrecy::" + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** 问题与答案须同时设置或同时清空。 */
    public static void assertConsistent(String question, String answer) {
        if ((question == null) != (answer == null)) {
            throw new PolicyException("密保问题与答案须同时填写或同时清空");
        }
    }
}
