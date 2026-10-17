package com.campus.charity.common.util;

import cn.hutool.core.util.StrUtil;

/**
 * 隐私脱敏工具。
 *
 * <p>需求：帮扶对象个人信息做脱敏展示；用户学号、捐赠信息仅限校内公益业务使用。</p>
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 姓名脱敏：张三 -> 张*，李小明 -> 李** */
    public static String maskName(String name) {
        if (StrUtil.isBlank(name)) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.length() <= 1) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(trimmed.charAt(0));
        for (int i = 1; i < trimmed.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    /** 手机号脱敏：13800138000 -> 138****8000 */
    public static String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return "";
        }
        String trimmed = phone.trim();
        if (trimmed.length() < 7) {
            return "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /** 身份证脱敏：保留前 6 位与后 4 位 */
    public static String maskIdCard(String idCard) {
        if (StrUtil.isBlank(idCard)) {
            return "";
        }
        String trimmed = idCard.trim();
        if (trimmed.length() < 10) {
            return "****";
        }
        return trimmed.substring(0, 6) + "********" + trimmed.substring(trimmed.length() - 4);
    }

    /** 学号 / 社团编号脱敏：2021000101 -> 2021****01 */
    public static String maskIdentityNo(String identityNo) {
        if (StrUtil.isBlank(identityNo)) {
            return "";
        }
        String trimmed = identityNo.trim();
        if (trimmed.length() <= 6) {
            return trimmed.charAt(0) + "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    /** 邮箱脱敏：abc@x.com -> a**@x.com */
    public static String maskEmail(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return "";
        }
        String[] parts = email.split("@", 2);
        String prefix = parts[0];
        String masked = prefix.length() <= 1 ? prefix : prefix.charAt(0) + "**";
        return masked + "@" + parts[1];
    }

    /** 账号脱敏：用于公示场景 */
    public static String maskAccount(String account) {
        if (StrUtil.isBlank(account)) {
            return "";
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "****" + trimmed.charAt(trimmed.length() - 1);
    }

    /** 捐赠人展示名：匿名捐赠统一显示为“爱心人士” */
    public static String donorDisplayName(String realName, boolean anonymous) {
        if (anonymous) {
            return "爱心人士";
        }
        return maskName(realName);
    }
}
