package com.campus.charity.common.util;

import cn.hutool.crypto.digest.DigestUtil;
import com.campus.charity.common.constant.BizTypeConst;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

/**
 * 哈希工具类。
 *
 * <p>核心法律约束：系统只把 SHA-256 摘要写入联盟链，原始文件与原始业务数据保存在业务服务器。</p>
 *
 * <p>为了让“溯源查询 + 哈希校验”可重复计算，每种业务的存证哈希都采用<b>确定性拼接规则</b>：</p>
 * <pre>
 * 项目：      SHA256("PROJECT|"      + projectId + "|" + title + "|" + targetAmount + "|" + beneficiaryMasked + "|" + startTime + "|" + endTime)
 * 捐赠：      SHA256("DONATION|"     + donationId + "|" + projectId + "|" + donorId + "|" + amount + "|" + donateTime)
 * 资金凭证：  SHA256("FUND_USAGE|"   + usageId + "|" + projectId + "|" + amount + "|" + usageDesc + "|" + fileHash)
 * 帮扶材料：  SHA256("MATERIAL|"     + materialId + "|" + projectId + "|" + materialType + "|" + fileHash)
 * 资金提取：  SHA256("WITHDRAWAL|"   + withdrawalId + "|" + projectId + "|" + amount + "|" + applyTime)
 * 项目进度：  SHA256("PROGRESS|"     + progressId + "|" + projectId + "|" + progressPercent + "|" + title)
 * 审计报告：  SHA256("AUDIT_REPORT|" + reportId + "|" + reportType + "|" + fileHash)
 * 项目状态：  SHA256("PROJECT_STATUS|"+ projectId + "|" + status + "|" + changeTime)
 * </pre>
 */
public final class HashUtil {

    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 捐赠人标识哈希盐值（链上只保存 donorHash，不保存学号 / 用户ID 明文） */
    private static final String DONOR_SALT = "campus-charity-donor-salt-2024";

    private HashUtil() {
    }

    public static String sha256Hex(String content) {
        return DigestUtil.sha256Hex(content == null ? "" : content);
    }

    public static String sha256Hex(byte[] bytes) {
        return DigestUtil.sha256Hex(bytes);
    }

    /** 流式计算文件哈希，避免大文件全部读入内存 */
    public static String sha256File(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return DigestUtil.sha256Hex(in);
        }
    }

    public static String md5Hex(String content) {
        return DigestUtil.md5Hex(content == null ? "" : content);
    }

    /** 捐赠人 / 用户标识脱敏哈希：链上不出现学号、用户ID等可识别信息 */
    public static String donorHash(Long userId) {
        return sha256Hex(DONOR_SALT + "|" + (userId == null ? "" : userId));
    }

    /** 身份证号哈希（实名认证用，不存明文） */
    public static String idCardHash(String idCard) {
        return sha256Hex("campus-charity-idcard-salt-2024|" + (idCard == null ? "" : idCard.trim()));
    }

    public static String projectHash(Long projectId, String title, BigDecimal targetAmount,
                                     String beneficiaryMasked, LocalDateTime startTime, LocalDateTime endTime) {
        return build(BizTypeConst.PROJECT, projectId, title, amount(targetAmount), beneficiaryMasked,
                time(startTime), time(endTime));
    }

    public static String donationHash(Long donationId, Long projectId, Long donorId, BigDecimal amount,
                                      LocalDateTime donateTime) {
        return build(BizTypeConst.DONATION, donationId, projectId, donorId, amount(amount), time(donateTime));
    }

    public static String fundUsageHash(Long usageId, Long projectId, BigDecimal amount, String usageDesc, String fileHash) {
        return build(BizTypeConst.FUND_USAGE, usageId, projectId, amount(amount), usageDesc, fileHash);
    }

    public static String materialHash(Long materialId, Long projectId, String materialType, String fileHash) {
        return build(BizTypeConst.MATERIAL, materialId, projectId, materialType, fileHash);
    }

    public static String withdrawalHash(Long withdrawalId, Long projectId, BigDecimal amount, LocalDateTime applyTime) {
        return build(BizTypeConst.WITHDRAWAL, withdrawalId, projectId, amount(amount), time(applyTime));
    }

    public static String progressHash(Long progressId, Long projectId, Integer progressPercent, String title) {
        return build(BizTypeConst.PROJECT_PROGRESS, progressId, projectId,
                progressPercent == null ? "0" : String.valueOf(progressPercent), title);
    }

    public static String reportHash(Long reportId, String reportType, String fileHash) {
        return build(BizTypeConst.AUDIT_REPORT, reportId, reportType, fileHash);
    }

    public static String projectStatusHash(Long projectId, String status, LocalDateTime changeTime) {
        return build(BizTypeConst.PROJECT_STATUS, projectId, status, time(changeTime));
    }

    private static String build(String bizType, Object... parts) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(bizType);
        for (Object part : parts) {
            joiner.add(part == null ? "" : String.valueOf(part));
        }
        return sha256Hex(joiner.toString());
    }

    private static String amount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
    }

    private static String time(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FORMAT);
    }

    /** 校验是否为 64 位十六进制 SHA-256 摘要 */
    public static boolean isSha256(String value) {
        return value != null && value.matches("^[0-9a-fA-F]{64}$");
    }

    public static String encodeBase64(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}
