package com.campus.charity.fabric.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上链存证请求载荷。
 *
 * <p>法律约束：所有字段均为哈希摘要或非敏感信息（金额、状态、业务编号、操作人账号），
 * 绝不包含姓名、学号、手机号、身份证号、住址等敏感隐私。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidencePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** PROJECT / DONATION / FUND_USAGE / MATERIAL / WITHDRAWAL / PROJECT_PROGRESS / AUDIT_REPORT / PROJECT_STATUS */
    private String bizType;
    private String eventKey;
    private String eventType;
    private String bizId;
    private String projectId;
    /** 存证数据哈希（SHA-256） */
    private String dataHash;
    /** 原始文件哈希（原始文件在业务服务器） */
    private String fileHash;
    /** 金额（非隐私，用于链上资金链追踪） */
    private String amount;
    private String amountHash;
    /** 捐赠人标识哈希（不上链明文用户ID） */
    private String donorHash;
    /** 操作人标识（账号或编号，不含姓名） */
    private String operator;
    private String operatorRole;
    private String status;
    /** 阶段说明（仅用于业务展示，不写入上链参数） */
    private String stage;
    /** 系统模板生成的说明（不含个人隐私） */
    private String remark;
    /** 扩展字段（仅非敏感值） */
    @Builder.Default
    private Map<String, String> extra = new LinkedHashMap<>();

    /** 业务侧关联的链下原始文件ID（仅用于本地 evidence_record，不上链） */
    private Long fileId;
    private Long operatorId;
    private String operatorName;
    private String functionName;
}
